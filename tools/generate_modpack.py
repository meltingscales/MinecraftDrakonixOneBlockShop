#!/usr/bin/env python3
"""Regenerates modpack/mods/*.pw.toml from build.gradle's `localRuntime "maven.modrinth:..."` and
`localRuntime "curse.maven:..."` lines - those are already the single, vetted source of truth
for every dev-dependency mod (see RECOMMENDED-MODS.md for the pin rationale next to each one).
Rather than hand-maintaining a second mod list for the modpack, this parses build.gradle,
resolves each Modrinth slug:version-or-id pin to a real project+version ID pair (needed because
`packwiz modrinth add` takes IDs, not Maven-style coordinates) or each Cursemaven
slug-projectId:fileId pin straight into its already-numeric ids, and shells out to the real
`packwiz` CLI - it already knows how to fetch hashes/URLs correctly, no need to reimplement that
here.

Requires the `packwiz` CLI on PATH (see justfile's `modpack-sync` recipe) and network access to
Modrinth's/CurseForge's APIs. Rerun this after adding/removing/repinning a localRuntime mod in
build.gradle - don't hand-edit modpack/mods/*.pw.toml, this wipes and regenerates that folder
every time.
"""
import json
import re
import shutil
import subprocess
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUILD_GRADLE = ROOT / "build.gradle"
MODPACK_DIR = ROOT / "modpack"

MODRINTH_PATTERN = re.compile(r'localRuntime\s+"maven\.modrinth:([^:]+):([^"]+)"')
CURSEFORGE_PATTERN = re.compile(r'localRuntime\s+"curse\.maven:[^":]+-(\d+):(\d+)"')


def fetch_json(url: str):
    with urllib.request.urlopen(url) as response:
        return json.loads(response.read())


def resolve_project_and_version(slug: str, pinned: str):
    """pinned is either a Modrinth version *number* (e.g. "10.7.14.79") or version *id*
    (e.g. "txp9wDw2", used when the version number collides across loaders - see
    RECOMMENDED-MODS.md). Try treating it as an id first (cheap, one request); fall back to
    scanning the project's version list for a matching version_number on a neoforge build."""
    project = fetch_json(f"https://api.modrinth.com/v2/project/{slug}")
    project_id = project["id"]

    try:
        version = fetch_json(f"https://api.modrinth.com/v2/version/{pinned}")
        if version["project_id"] == project_id:
            return project_id, version["id"]
    except urllib.error.HTTPError:
        pass

    for version in fetch_json(f"https://api.modrinth.com/v2/project/{slug}/version"):
        if version["version_number"] == pinned and "neoforge" in version["loaders"]:
            return project_id, version["id"]

    raise RuntimeError(f"Could not resolve {slug}:{pinned} to a Modrinth version - check the pin in build.gradle")


def main():
    build_gradle_text = BUILD_GRADLE.read_text(encoding="utf-8")
    modrinth_deps = MODRINTH_PATTERN.findall(build_gradle_text)
    curseforge_deps = CURSEFORGE_PATTERN.findall(build_gradle_text)
    if not modrinth_deps and not curseforge_deps:
        raise RuntimeError("No localRuntime maven.modrinth/curse.maven dependencies found in build.gradle")

    mods_dir = MODPACK_DIR / "mods"
    if mods_dir.exists():
        shutil.rmtree(mods_dir)
        # Without this, index.toml still references the just-deleted files until the next
        # refresh, and packwiz's own dependency-tracking (e.g. AE2 pulling in GuideMe) warns
        # trying to read them mid-loop below.
        subprocess.run(["packwiz", "refresh"], cwd=MODPACK_DIR, check=True)

    for slug, pinned in modrinth_deps:
        project_id, version_id = resolve_project_and_version(slug, pinned)
        print(f"Adding {slug} ({pinned} -> project {project_id}, version {version_id})", flush=True)
        subprocess.run(
            ["packwiz", "modrinth", "add", "--project-id", project_id, "--version-id", version_id, "-y"],
            cwd=MODPACK_DIR, check=True,
        )

    for project_id, file_id in curseforge_deps:
        print(f"Adding curseforge project {project_id} (file {file_id})", flush=True)
        subprocess.run(
            ["packwiz", "curseforge", "add", "--addon-id", project_id, "--file-id", file_id, "-y"],
            cwd=MODPACK_DIR, check=True,
        )

    subprocess.run(["packwiz", "refresh"], cwd=MODPACK_DIR, check=True)
    print(f"Synced {len(modrinth_deps) + len(curseforge_deps)} mods into modpack/mods/")


if __name__ == "__main__":
    main()

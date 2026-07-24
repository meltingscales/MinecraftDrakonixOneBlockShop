package com.drakonix.oneblockshop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// Structural validation of pricing/seed_prices*.json only - no Minecraft classes involved, on
// purpose. Plain JUnit (this project's `test` task) can't touch net.minecraft.world.level.block
// classes: NeoForge patches BlockBehaviour.Properties to consult FML's LoadingModList, which is
// only populated when the game is actually launched through ModLauncher (runClient/runServer/
// runGameTestServer) - Bootstrap.bootStrap() throws NPE outside that environment. So this test
// can't confirm "minecraft:dirt" resolves to a real Item, or that a tag ID is spelled the way
// the mod that owns it actually spells it; it catches the failure modes a hand-edited JSON file
// actually has - malformed JSON, bad ID shape, non-positive prices - which is still real
// coverage for files with no other validation today.
class PricingSeedTest
{
    // Covers both plain item IDs (minecraft:dirt) and tag IDs (c:ores/copper) - tags just allow
    // extra path segments.
    private static final Pattern RESOURCE_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private JsonObject loadJson(String resourcePath) throws IOException
    {
        try (InputStream stream = getClass().getResourceAsStream(resourcePath))
        {
            assertTrue(stream != null, resourcePath + " must be on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "/pricing/seed_prices.json", "/pricing/seed_prices_by_tag.json" })
    void fileParsesAndIsNonEmpty(String resourcePath) throws IOException
    {
        JsonObject json = loadJson(resourcePath);
        assertFalse(json.entrySet().isEmpty(), resourcePath + " should have at least one entry");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/pricing/seed_prices.json", "/pricing/seed_prices_by_tag.json" })
    void everyKeyLooksLikeAResourceId(String resourcePath) throws IOException
    {
        for (Map.Entry<String, JsonElement> entry : loadJson(resourcePath).entrySet())
            assertTrue(RESOURCE_ID.matcher(entry.getKey()).matches(),
                    "'" + entry.getKey() + "' in " + resourcePath + " doesn't look like a namespace:path ID");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/pricing/seed_prices.json", "/pricing/seed_prices_by_tag.json" })
    void everyPriceIsAPositiveWholeNumber(String resourcePath) throws IOException
    {
        for (Map.Entry<String, JsonElement> entry : loadJson(resourcePath).entrySet())
        {
            JsonElement value = entry.getValue();
            assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
                    "'" + entry.getKey() + "' price in " + resourcePath + " isn't a number");
            long price = value.getAsLong();
            assertTrue(price > 0, "'" + entry.getKey() + "' in " + resourcePath + " has a non-positive price: " + price);
        }
    }
}

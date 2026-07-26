package com.drakonix.oneblockshop;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The "vertical border" from the README: a vanilla WorldBorder, starting at exactly 1 block
// wide and centered on wherever the first player actually spawns (not the world's nominal
// spawn point, which vanilla can nudge a few blocks away when picking safe ground). Expanding
// it is a deliberate purchase from the shop GUI's Border tab - see ShopMenu.clickMenuButton -
// each expansion costing more than the last.
// ponytail: one shared world border for the whole overworld, not per-player - real per-player
// borders need mixins this mod doesn't have (attempted once and reverted, see TODO.md). Instead,
// once a second player's ever been online (see clampForMultiplayer), the shared border is just
// guaranteed to never be smaller than a fair starting size - simpler than managing several.
@EventBusSubscriber(modid = OneBlockShopMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class Border
{
    private static final double MAX_STRAY_BLOCKS = 5.0;

    // Once two or more players have ever been online together, the shared border can never be
    // smaller than this again (never shrinks it if already bigger) - the simple alternative to
    // real per-player borders (attempted once, reverted - see TODO.md): one shared border, just
    // guaranteed not to still be the punishing 1x1 a solo player would've started with. 17, not
    // 16 - every real expansion adds 2 to an odd starting size (1, 3, 5, ...), so 17 is the
    // nearest size on that same lattice that's still >= the requested 16, keeping
    // purchaseCount()'s (size-1)/2 math exact instead of introducing a fractional purchase count.
    private static final double MULTIPLAYER_MIN_SIZE = 17.0;

    // Cost of the Nth expansion (0-indexed) is BASE * GROWTH_FACTOR^N, each purchase widens
    // the border by 2 (1 block each side). Tuned against SELL_PRICES: early purchases are a
    // few stacks of cheap goods, later ones need real automation.
    private static final long BASE_COST = 25L;
    private static final double GROWTH_FACTOR = 1.2;

    // Rate-limits spam-clicking the expand button, independent of affordability.
    private static final long EXPANSION_COOLDOWN_TICKS = 30L * 20L;

    // Every expansion after the very first (1x1 -> 3x3, which a brand-new player has no way to
    // prepare for) calls in a monster wave scaled to how many expansions have already happened -
    // border growth stops being free real estate and starts being a real risk to defend.
    private static final int WAVE_BASE_SIZE = 2;
    private static final int WAVE_SIZE_PER_PURCHASE = 1;
    private static final int WAVE_MAX_SIZE = 12;
    private static final double WAVE_RING_MARGIN = 3.0;
    private static final List<EntityType<? extends Monster>> WAVE_MOBS =
            List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OneBlockShopMod.MODID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> INITIALIZED = ATTACHMENTS.register(
            "border_initialized", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> LAST_EXPANSION_TICK = ATTACHMENTS.register(
            "last_expansion_tick", () -> AttachmentType.builder(() -> Long.MIN_VALUE).serialize(Codec.LONG).build());
    // How many players currently have an Expedition teleport in flight - see
    // beginExpeditionHold/endExpeditionHold below.
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> EXPEDITIONS_ACTIVE = ATTACHMENTS.register(
            "expeditions_active", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> HOME_SIZE_SNAPSHOT = ATTACHMENTS.register(
            "home_size_snapshot", () -> AttachmentType.builder(() -> 1.0).serialize(Codec.DOUBLE).build());

    private Border() {}

    public static void initIfNeeded(ServerPlayer player)
    {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        if (overworld.getData(INITIALIZED))
            return;
        overworld.setData(INITIALIZED, true);

        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(player.getX(), player.getZ());
        border.setSize(1.0);

        // Fairness: a 1-block border means spawning over lava/void is a real risk the player
        // never chose. keepInventory softens that without touching difficulty/mob damage.
        overworld.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, overworld.getServer());
    }

    // Called on every login - see MULTIPLAYER_MIN_SIZE. Safe to call unconditionally: a no-op
    // once already clamped (setSize only ever moves up here), and skipped entirely while an
    // Expedition hold has the border at its temporary safe-travel size (see
    // beginExpeditionHold/endExpeditionHold) so this can't stomp that placeholder.
    public static void clampForMultiplayer(ServerLevel overworld)
    {
        if (overworld.getServer().getPlayerList().getPlayerCount() < 2)
            return;
        forceMultiplayerClamp(overworld);
    }

    // For /drakonixoneblockshop border simulatejoin - a solo tester has no second real account
    // to actually trigger clampForMultiplayer's player-count check, so this applies the same
    // clamp unconditionally.
    public static void forceMultiplayerClamp(ServerLevel overworld)
    {
        if (overworld.getData(EXPEDITIONS_ACTIVE) > 0)
            return;
        WorldBorder border = overworld.getWorldBorder();
        if (border.getSize() < MULTIPLAYER_MIN_SIZE)
            border.setSize(MULTIPLAYER_MIN_SIZE);
    }

    // How many expansions have already been bought, derived from the border's current size
    // rather than tracked separately - the WorldBorder itself is the single source of truth.
    public static int purchaseCount(WorldBorder border)
    {
        return (int) Math.round((border.getSize() - 1.0) / 2.0);
    }

    public static long costForNextExpansion(WorldBorder border)
    {
        return Math.round(BASE_COST * Math.pow(GROWTH_FACTOR, purchaseCount(border)));
    }

    // Whole seconds left before this player can expand again, 0 if they're free to. Exposed for
    // the GUI (ShopMenu/ShopScreen) to show a countdown instead of a button that silently fails.
    public static long cooldownRemainingSeconds(Player player)
    {
        long last = player.getData(LAST_EXPANSION_TICK);
        if (last == Long.MIN_VALUE)
            return 0L; // never expanded yet, not on cooldown
        long elapsed = player.level().getGameTime() - last;
        long remainingTicks = Math.max(0L, EXPANSION_COOLDOWN_TICKS - elapsed);
        return (remainingTicks + 19L) / 20L; // ceil to whole seconds
    }

    // Returns whether the purchase went through (false if the player couldn't afford it, or is
    // still on cooldown from their last purchase).
    public static boolean tryExpand(ServerPlayer player)
    {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        // The shared border's current size is a temporary Expedition placeholder right now, not
        // real progress - expanding it would corrupt that placeholder instead of buying anything.
        if (overworld.getData(EXPEDITIONS_ACTIVE) > 0)
            return false;
        if (cooldownRemainingSeconds(player) > 0)
            return false;

        WorldBorder border = overworld.getWorldBorder();
        long cost = costForNextExpansion(border);
        if (Wallet.get(player) < cost)
            return false;

        int purchaseCountBefore = purchaseCount(border);
        Wallet.add(player, -cost);
        border.setSize(border.getSize() + 2.0);
        player.setData(LAST_EXPANSION_TICK, player.level().getGameTime());

        // Skip the very first expansion (1x1 -> 3x3) - a brand-new player with nothing built
        // yet has no way to defend against a wave.
        if (purchaseCountBefore > 0)
            spawnMobWave(player, border, purchaseCountBefore);

        return true;
    }

    private static void spawnMobWave(ServerPlayer player, WorldBorder border, int purchaseCountBefore)
    {
        if (!(player.level() instanceof ServerLevel level))
            return;

        int waveSize = Math.min(WAVE_MAX_SIZE, WAVE_BASE_SIZE + purchaseCountBefore * WAVE_SIZE_PER_PURCHASE);
        double radius = border.getSize() / 2.0 + WAVE_RING_MARGIN;
        RandomSource random = level.getRandom();

        for (int i = 0; i < waveSize; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int x = (int) Math.round(border.getCenterX() + Math.cos(angle) * radius);
            int z = (int) Math.round(border.getCenterZ() + Math.sin(angle) * radius);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));

            EntityType<? extends Monster> type = WAVE_MOBS.get(random.nextInt(WAVE_MOBS.size()));
            type.spawn(level, surface, MobSpawnType.EVENT);
        }

        player.sendSystemMessage(Component.literal(
                "A wave of " + waveSize + " monsters closes in on your new border!").withStyle(ChatFormatting.RED));
    }

    // Growing the one shared WorldBorder is the only mixin-free way to let a player on an
    // Expedition roam far without vanilla's own border push/damage (and onPlayerTick's stray
    // safety-net below) kicking in - there's no per-player WorldBorder without mixins, same
    // limitation noted for the reverted per-player-borders attempt in TODO.md. Fine for
    // singleplayer; a second player still at home sees their border balloon out for the
    // expedition's duration too - known shortcut, not worth mixins to fix.
    public static void beginExpeditionHold(ServerLevel overworld, double minimumSize)
    {
        int active = overworld.getData(EXPEDITIONS_ACTIVE);
        if (active == 0)
            overworld.setData(HOME_SIZE_SNAPSHOT, overworld.getWorldBorder().getSize());
        overworld.setData(EXPEDITIONS_ACTIVE, active + 1);
        if (overworld.getWorldBorder().getSize() < minimumSize)
            overworld.getWorldBorder().setSize(minimumSize);
    }

    // Restores the real border once the last player currently away returns.
    public static void endExpeditionHold(ServerLevel overworld)
    {
        int active = Math.max(0, overworld.getData(EXPEDITIONS_ACTIVE) - 1);
        overworld.setData(EXPEDITIONS_ACTIVE, active);
        if (active == 0)
        {
            overworld.getWorldBorder().setSize(overworld.getData(HOME_SIZE_SNAPSHOT));
            // A second player could've logged in while the hold was active - clampForMultiplayer
            // skips itself entirely during a hold (see above), so re-check now that it's over.
            clampForMultiplayer(overworld);
        }
    }

    // Safety net against straying past the border (bugs, other mods, admin teleports) - vanilla
    // already pushes/damages players at the edge, this just guarantees they can't drift far.
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide)
            return;

        ServerLevel overworld = player.serverLevel().getServer().overworld();
        if (player.serverLevel() != overworld)
            return;

        WorldBorder border = overworld.getWorldBorder();
        if (border.getDistanceToBorder(player) < -MAX_STRAY_BLOCKS)
            player.teleportTo(border.getCenterX(), player.getY(), border.getCenterZ());
    }
}

package com.drakonix.oneblockshop;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /drakonixoneblockshop <balance|border|starterkit|devcheat|hardmode> - op-only (level 2, same
// gate as /gamemode) cheat commands for testing/admin use: adjust a player's wallet, adjust the
// shared border directly (bypassing the shop's purchase cost), re-issue the starter kit, trigger
// various things (expedition teleport/countdown, hopper report, border wave, portal) instantly
// instead of waiting out their real timers, or remove a player's own Permanent Hard Mode lock
// (PlayerSettings) - the one setting a player genuinely can't undo themselves by design.
// /drakonixoneblockshop <expedition|help> - open to any player: end your own Explore-tab trip
// early, or list what's available.
// The requires(level 2) check is on each admin subcommand's own literal, not the root - a
// Brigadier parent's requires() would gate every child under it, including the player-facing
// ones, which is why it can't just sit on "drakonixoneblockshop" itself.
@EventBusSubscriber(modid = OneBlockShopMod.MODID)
public final class AdminCommands
{
    private AdminCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("drakonixoneblockshop")
                .then(Commands.literal("balance")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("get")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(AdminCommands::balanceGet)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(AdminCommands::balanceSet))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("amount", LongArgumentType.longArg())
                                                .executes(AdminCommands::balanceAdd)))))
                .then(Commands.literal("border")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("get")
                                .executes(AdminCommands::borderGet))
                        .then(Commands.literal("set")
                                .then(Commands.argument("size", DoubleArgumentType.doubleArg(1.0))
                                        .executes(AdminCommands::borderSet)))
                        .then(Commands.literal("expand")
                                .executes(AdminCommands::borderExpand))
                        .then(Commands.literal("simulatejoin")
                                .executes(AdminCommands::borderSimulateJoin)))
                .then(Commands.literal("starterkit")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("give")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(AdminCommands::starterKitGive))))
                .then(Commands.literal("expedition")
                        .then(Commands.literal("end")
                                .executes(AdminCommands::expeditionEnd)))
                .then(Commands.literal("hardmode")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(AdminCommands::hardModeUnlock))))
                .then(Commands.literal("devcheat")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("expedition")
                                .then(Commands.literal("teleport")
                                        .executes(AdminCommands::devExpeditionTeleport))
                                .then(Commands.literal("fastforward")
                                        .then(Commands.argument("seconds", LongArgumentType.longArg(0))
                                                .executes(AdminCommands::devExpeditionFastForward))))
                        .then(Commands.literal("hopperreport")
                                .executes(AdminCommands::devHopperReport))
                        .then(Commands.literal("borderwave")
                                .executes(AdminCommands::devBorderWave))
                        .then(Commands.literal("closeportal")
                                .executes(AdminCommands::devClosePortal)))
                .then(Commands.literal("help")
                        .executes(AdminCommands::help)));
    }

    private static int balanceGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        long balance = Wallet.get(target);
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + "'s balance: " + balance), false);
        return (int) balance;
    }

    private static int balanceSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        long amount = LongArgumentType.getLong(ctx, "amount");
        Wallet.add(target, amount - Wallet.get(target));
        ctx.getSource().sendSuccess(() -> Component.literal("Set " + target.getName().getString() + "'s balance to " + amount), true);
        return 1;
    }

    private static int balanceAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        long amount = LongArgumentType.getLong(ctx, "amount");
        long newBalance = Wallet.add(target, amount);
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + "'s balance is now " + newBalance), true);
        return 1;
    }

    private static int borderGet(CommandContext<CommandSourceStack> ctx)
    {
        WorldBorder border = overworld(ctx).getWorldBorder();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Border size: " + border.getSize() + " (next expansion costs " + Border.costForNextExpansion(border) + ")"), false);
        return (int) border.getSize();
    }

    private static int borderSet(CommandContext<CommandSourceStack> ctx)
    {
        double size = DoubleArgumentType.getDouble(ctx, "size");
        overworld(ctx).getWorldBorder().setSize(size);
        ctx.getSource().sendSuccess(() -> Component.literal("Border size set to " + size), true);
        return (int) size;
    }

    private static int borderExpand(CommandContext<CommandSourceStack> ctx)
    {
        WorldBorder border = overworld(ctx).getWorldBorder();
        double newSize = border.getSize() + 2.0;
        border.setSize(newSize);
        ctx.getSource().sendSuccess(() -> Component.literal("Border expanded for free, now " + newSize), true);
        return (int) newSize;
    }

    // For testing Border.clampForMultiplayer without a second real account - applies the same
    // clamp (Border.forceMultiplayerClamp) the real player-count check would trigger.
    private static int borderSimulateJoin(CommandContext<CommandSourceStack> ctx)
    {
        ServerLevel overworld = overworld(ctx);
        Border.forceMultiplayerClamp(overworld);
        double size = overworld.getWorldBorder().getSize();
        ctx.getSource().sendSuccess(() -> Component.literal("Simulated a second player joining - border size is now " + size), true);
        return (int) size;
    }

    private static int starterKitGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        StarterKit.giveItems(target);
        ctx.getSource().sendSuccess(() -> Component.literal("Gave the starter kit to " + target.getName().getString()), true);
        return 1;
    }

    private static int expeditionEnd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        // Expedition.tryEndEarly already sends its own "Returned to base." chat line - no
        // separate sendSuccess needed on top of that.
        if (!Expedition.tryEndEarly(player))
        {
            ctx.getSource().sendFailure(Component.literal("You're not currently on an expedition."));
            return 0;
        }
        return 1;
    }

    private static int devExpeditionTeleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        // Expedition.devTeleport already sends its own "Through the portal!" chat line.
        if (!Expedition.devTeleport(player))
        {
            ctx.getSource().sendFailure(Component.literal("Already on an expedition."));
            return 0;
        }
        return 1;
    }

    private static int devExpeditionFastForward(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        long seconds = LongArgumentType.getLong(ctx, "seconds");
        if (!Expedition.devFastForward(player, seconds))
        {
            ctx.getSource().sendFailure(Component.literal("Not currently on an expedition."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Expedition time remaining set to " + seconds + "s"), true);
        return 1;
    }

    // The only way off of a player's own Permanent Hard Mode lock (PlayerSettings) once they've
    // set it themselves - deliberately requires an op, same as every other admin subcommand here.
    private static int hardModeUnlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        PlayerSettings.adminUnlock(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Unlocked " + target.getName().getString() + "'s Permanent Hard Mode settings."), true);
        return 1;
    }

    private static int devHopperReport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        // HopperSalesTracker.devForceReport already sends its own message(s).
        HopperSalesTracker.devForceReport(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int devBorderWave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        // Border.devSpawnMobWave already sends its own "monsters closes in" chat line.
        Border.devSpawnMobWave(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int devClosePortal(CommandContext<CommandSourceStack> ctx)
    {
        if (!Expedition.devClosePortal(overworld(ctx)))
        {
            ctx.getSource().sendFailure(Component.literal("No portal is currently open."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Portal closed."), true);
        return 1;
    }

    // Deliberately unfiltered - lists every subcommand regardless of whether the caller can
    // actually use the op-only ones, same as vanilla /help listing commands you may not have
    // permission for.
    private static int help(CommandContext<CommandSourceStack> ctx)
    {
        List<String> lines = List.of(
                "/drakonixoneblockshop expedition end - end your current Explore-tab trip early",
                "/drakonixoneblockshop help - show this list",
                "/drakonixoneblockshop balance <get|set|add> <player> [amount] - op: read/adjust a wallet",
                "/drakonixoneblockshop border get - op: read the shared world border's size",
                "/drakonixoneblockshop border set <size> - op: set the shared world border's size",
                "/drakonixoneblockshop border expand - op: expand the shared world border for free",
                "/drakonixoneblockshop border simulatejoin - op: simulate a second player joining, for testing the multiplayer border clamp",
                "/drakonixoneblockshop starterkit give <player> - op: re-issue the starter kit",
                "/drakonixoneblockshop devcheat expedition teleport - op: skip the portal, go on an expedition right now",
                "/drakonixoneblockshop devcheat expedition fastforward <seconds> - op: jump your expedition countdown to N seconds left",
                "/drakonixoneblockshop devcheat hopperreport - op: force your hopper-sales report to fire now",
                "/drakonixoneblockshop devcheat borderwave - op: spawn a border-expansion mob wave without buying one",
                "/drakonixoneblockshop devcheat closeportal - op: force-close a stuck/open Explore-tab portal",
                "/drakonixoneblockshop hardmode unlock <player> - op: remove a player's self-imposed Permanent Hard Mode lock");
        for (String line : lines)
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return lines.size();
    }

    private static ServerLevel overworld(CommandContext<CommandSourceStack> ctx)
    {
        return ctx.getSource().getServer().overworld();
    }
}

package com.drakonix.oneblockshop;

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

// /drakonixoneblockshop <balance|border|starterkit> - op-only (level 2, same gate as /gamemode)
// cheat commands for testing/admin use: adjust a player's wallet, adjust the shared border
// directly (bypassing the shop's purchase cost), or re-issue the starter kit.
@EventBusSubscriber(modid = OneBlockShopMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AdminCommands
{
    private AdminCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("drakonixoneblockshop")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("balance")
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
                        .then(Commands.literal("get")
                                .executes(AdminCommands::borderGet))
                        .then(Commands.literal("set")
                                .then(Commands.argument("size", DoubleArgumentType.doubleArg(1.0))
                                        .executes(AdminCommands::borderSet)))
                        .then(Commands.literal("expand")
                                .executes(AdminCommands::borderExpand)))
                .then(Commands.literal("starterkit")
                        .then(Commands.literal("give")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(AdminCommands::starterKitGive)))));
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

    private static int starterKitGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        StarterKit.giveItems(target);
        ctx.getSource().sendSuccess(() -> Component.literal("Gave the starter kit to " + target.getName().getString()), true);
        return 1;
    }

    private static ServerLevel overworld(CommandContext<CommandSourceStack> ctx)
    {
        return ctx.getSource().getServer().overworld();
    }
}

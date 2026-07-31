package com.drakonix.oneblockshop;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;

// Kept separate from Expedition.java (rather than adding this handler there) specifically so
// that class never has to import a client-only class - GatherEffectScreenTooltipsEvent only
// ever fires on the physical client, but merely referencing it from a class that also loads on
// a dedicated server risks NoClassDefFoundError.
//
// Vanilla has no built-in "description" for potion effects - EffectRenderingInventoryScreen's
// hover tooltip only ever shows name + duration. This event (fired from
// EffectRenderingInventoryScreen.renderEffects, confirmed in decompiled source) is NeoForge's
// hook for appending extra lines to that tooltip.
@EventBusSubscriber(modid = OneBlockShopMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ExpeditionClientEvents
{
    private ExpeditionClientEvents() {}

    @SubscribeEvent
    public static void onEffectTooltip(GatherEffectScreenTooltipsEvent event)
    {
        if (event.getEffectInstance().getEffect().value() == Expedition.EFFECT.get())
            event.getTooltip().add(Component.translatable("effect.drakonixoneblockshop.expedition.description"));
        if (event.getEffectInstance().getEffect().value() == Expedition.PORTAL_IMMUNITY_EFFECT.get())
            event.getTooltip().add(Component.translatable("effect.drakonixoneblockshop.portal_immunity.description"));
    }
}

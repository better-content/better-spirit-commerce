package com.bettercontent.spiritcommerce.registry;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BetterSpiritCommerce.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CurrencyItemColors {
    private CurrencyItemColors() {}

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Item event) {
        for (CurrencyIdentity identity : CurrencyIdentity.values()) {
            int color = switch (identity) {
                case POWER -> 0xFF4055;
                case MOTION -> 0xE0B01F;
                case ENDURANCE -> 0x52606A;
                case RENEWAL -> 0x6CCAF0;
                case CONTROL -> 0x8E5BB7;
            };
            event.register((stack, tintIndex) -> color, CurrencyItems.item(identity).get());
        }
    }
}

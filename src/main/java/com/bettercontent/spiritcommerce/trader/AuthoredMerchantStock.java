package com.bettercontent.spiritcommerce.trader;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.registries.ForgeRegistries;

/** Connects the finite-stock policy to the authored seller types and their completed trades. */
public final class AuthoredMerchantStock {
    private AuthoredMerchantStock() {}

    public static boolean manages(final AbstractVillager merchant, final MerchantOffer offer) {
        return merchant != null && offer != null && AuthoredStockPolicy.isFinite(offer);
    }

    static boolean manages(final ResourceLocation sellerType, final ResourceLocation commodity) {
        return manages(sellerType, commodity, false);
    }

    static boolean manages(final ResourceLocation sellerType, final ResourceLocation commodity,
                           final boolean authoredDailyOffer) {
        // A daily Doctor refresh changes its local menu, not shared physical import stock.
        return AuthoredStockPolicy.isFinite(commodity);
    }

    public static boolean canPickup(final AbstractVillager merchant, final MerchantOffer offer,
                                    final ServerLevel level) {
        return !manages(merchant, offer) || AuthoredStockData.get(level).canPurchase(offer);
    }

    /** Custom Merchant implementations that use the vanilla result slot share commodity stock too. */
    public static boolean canPickup(final Merchant merchant, final MerchantOffer offer,
                                    final ServerLevel level) {
        if (merchant instanceof AbstractVillager villager) return canPickup(villager, offer, level);
        return offer == null || !AuthoredStockPolicy.isFinite(offer) || !managesCustomMerchantOutput(false,
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(offer.getResult().getItem()))
                || AuthoredStockData.get(level).canPurchase(offer);
    }

    static boolean managesCustomMerchantOutput(final boolean abstractVillager,
                                               final ResourceLocation output) {
        return !abstractVillager && AuthoredStockPolicy.isFinite(output);
    }

    /** AbstractVillager trades are debited by Forge's successful-trade event; this covers other Merchant types. */
    public static void recordCustomMerchantTrade(final Merchant merchant, final MerchantOffer offer,
                                                 final ServerLevel level) {
        if (offer == null || !AuthoredStockPolicy.isFinite(offer)
                || !managesCustomMerchantOutput(merchant instanceof AbstractVillager,
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(offer.getResult().getItem()))) return;
        if (!AuthoredStockData.get(level).recordPurchase(offer)) {
            throw new IllegalStateException("Completed custom merchant trade without finite stock: " + offer);
        }
    }

    public static int availableTrades(final AbstractVillager merchant, final MerchantOffer offer,
                                      final ServerLevel level, final int nativeUsesRemaining) {
        if (!manages(merchant, offer)) return nativeUsesRemaining;
        int outputUnits = offer.getResult().getCount();
        if (outputUnits <= 0) return 0;
        return Math.min(nativeUsesRemaining, AuthoredStockData.get(level).remainingUnits(offer) / outputUnits);
    }

    public static boolean recordTrade(final AbstractVillager merchant, final MerchantOffer offer) {
        if (!manages(merchant, offer) || !(merchant.level() instanceof ServerLevel level)) return true;
        return AuthoredStockData.get(level).recordPurchase(offer);
    }
}

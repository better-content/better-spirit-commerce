package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class AuthoredMerchantStockTest {
    private static final ResourceLocation DIAMOND = new ResourceLocation("minecraft", "diamond");
    private static final ResourceLocation PLAGUE_DOCTOR = new ResourceLocation("rats", "plague_doctor");

    @Test void ordinaryAndModdedAbstractVillagerSellersShareTheFinitePool() {
        assertTrue(AuthoredMerchantStock.manages(new ResourceLocation("minecraft", "villager"), DIAMOND));
        assertTrue(AuthoredMerchantStock.manages(new ResourceLocation("minecraft", "wandering_trader"), DIAMOND));
        assertTrue(AuthoredMerchantStock.manages(new ResourceLocation("modded", "custom_villager"), DIAMOND));
    }

    @Test void supplementariesRedMerchantSharesTheFinitePool() {
        assertTrue(AuthoredMerchantStock.manages(new ResourceLocation("supplementaries", "red_merchant"), DIAMOND));
    }

    @Test void customMerchantInterfacesUseTheFiniteCommodityPolicy() {
        assertTrue(AuthoredMerchantStock.managesCustomMerchantOutput(false, DIAMOND));
        assertFalse(AuthoredMerchantStock.managesCustomMerchantOutput(false,
                new ResourceLocation("minecraft", "bread")));
        assertFalse(AuthoredMerchantStock.managesCustomMerchantOutput(true, DIAMOND));
    }

    @Test void dailyDoctorListingsDoNotBypassTheSharedCommodityPool() {
        assertTrue(AuthoredMerchantStock.manages(PLAGUE_DOCTOR, DIAMOND));
        assertTrue(AuthoredMerchantStock.manages(PLAGUE_DOCTOR, DIAMOND, true));
        assertTrue(AuthoredMerchantStock.manages(PLAGUE_DOCTOR, DIAMOND, false));
        assertTrue(AuthoredMerchantStock.manages(new ResourceLocation("modded", "custom_villager"), DIAMOND, true));
        assertFalse(AuthoredMerchantStock.manages(new ResourceLocation("minecraft", "villager"),
                new ResourceLocation("minecraft", "bread")));
    }
}

package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class AuthoredStockPolicyTest {
    @Test void registeredListingCapacitiesAggregateByOutputCommodityOnly() {
        ResourceLocation diamondAxe = new ResourceLocation("minecraft", "diamond_axe");
        Map<String, Integer> stock = AuthoredStockPolicy.aggregate(List.of(
                new AuthoredStockPolicy.StockOffer(diamondAxe, 1, 3),
                new AuthoredStockPolicy.StockOffer(diamondAxe, 1, 3),
                new AuthoredStockPolicy.StockOffer(diamondAxe, 2, 2),
                new AuthoredStockPolicy.StockOffer(new ResourceLocation("minecraft", "bread"), 1, 16)));

        assertEquals(10, stock.get("minecraft:diamond_axe"));
        assertFalse(stock.containsKey("minecraft:bread"));
        assertEquals(1, stock.size());
    }

    @Test void explicitPoolPreservesLegacyCommodityCeilingsInsteadOfRecountingMenus() {
        Map<String, Integer> stock = AuthoredStockPolicy.initialStockUnits();

        assertEquals(64, stock.entrySet().stream()
                .filter(entry -> AuthoredStockPolicy.legacyGlobalCommodity(entry.getKey()))
                .mapToInt(Map.Entry::getValue).sum());
        assertEquals(4, stock.get("minecraft:diamond"));
        assertEquals(7, stock.get("minecraft:diamond_boots"));
        assertEquals(7, stock.get("minecraft:diamond_helmet"));
        assertEquals(5, stock.get("minecraft:diamond_chestplate"));
        assertEquals(5, stock.get("minecraft:diamond_leggings"));
        assertEquals(6, stock.get("minecraft:diamond_axe"));
        assertEquals(3, stock.get("minecraft:diamond_shovel"));
        assertEquals(3, stock.get("minecraft:diamond_pickaxe"));
        assertEquals(3, stock.get("minecraft:diamond_hoe"));
        assertEquals(3, stock.get("minecraft:diamond_sword"));
        assertEquals(4, stock.get("minecraft:netherite_scrap"));
        assertEquals(4, stock.get("minecraft:elytra"));
        assertEquals(1, stock.get("minecraft:netherite_ingot"));
        for (String path : List.of("netherite_boots", "netherite_helmet", "netherite_chestplate",
                "netherite_leggings", "netherite_sword", "netherite_axe", "netherite_pickaxe",
                "netherite_shovel", "netherite_hoe")) {
            assertEquals(1, stock.get("minecraft:" + path), path);
        }
    }

    @Test void finiteClassCoversNativeDiamondSalesAndCurrentAuthoredGoods() {
        for (String path : List.of("diamond", "diamond_boots", "diamond_helmet", "diamond_chestplate",
                "diamond_leggings", "diamond_axe", "diamond_sword", "diamond_pickaxe", "diamond_shovel",
                "diamond_hoe", "netherite_scrap", "netherite_ingot", "netherite_boots",
                "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_sword",
                "netherite_axe", "netherite_pickaxe", "netherite_shovel", "netherite_hoe", "elytra")) {
            assertTrue(AuthoredStockPolicy.isFinite(new ResourceLocation("minecraft", path)), path);
        }
        assertFalse(AuthoredStockPolicy.isFinite(new ResourceLocation("minecraft", "bread")));
        assertFalse(AuthoredStockPolicy.isFinite(new ResourceLocation("minecraft", "emerald")));
    }

    @Test void newImportCeilingsApplyToSpiritSalesNotIndependentResidentBarter() {
        ResourceLocation milk = new ResourceLocation("minecraft:milk_bucket");
        assertTrue(AuthoredStockPolicy.isFinite(milk, true));
        assertFalse(AuthoredStockPolicy.isFinite(milk, false));
        ResourceLocation diamond = new ResourceLocation("minecraft:diamond");
        assertTrue(AuthoredStockPolicy.isFinite(diamond, true));
        assertTrue(AuthoredStockPolicy.isFinite(diamond, false));
        assertFalse(AuthoredStockPolicy.isFinite(new ResourceLocation("minecraft:bread"), true));
    }

    @Test void netheriteRowsShareOneCommodityPoolAcrossDifferentOfferShapes() {
        ResourceLocation ingot = new ResourceLocation("minecraft", "netherite_ingot");
        Map<String, Integer> stock = AuthoredStockPolicy.aggregate(List.of(
                new AuthoredStockPolicy.StockOffer(ingot, 1, 1),
                new AuthoredStockPolicy.StockOffer(ingot, 2, 2),
                new AuthoredStockPolicy.StockOffer(ingot, 1, 3)));

        assertEquals(8, stock.get("minecraft:netherite_ingot"));
        assertEquals(1, stock.size());

        AuthoredStockLedger ledger = new AuthoredStockLedger();
        ledger.seed("minecraft:netherite_ingot", stock.get("minecraft:netherite_ingot"));
        assertTrue(ledger.purchase("minecraft:netherite_ingot", 2));
        assertTrue(ledger.purchase("minecraft:netherite_ingot", 6));
        assertFalse(ledger.purchase("minecraft:netherite_ingot", 1));
        assertEquals(0, ledger.remaining("minecraft:netherite_ingot"));
    }
}

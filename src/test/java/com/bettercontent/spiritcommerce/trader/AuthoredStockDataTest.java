package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class AuthoredStockDataTest {
    @Test void pickupGateRejectsDepletedAndInsufficientStockWithoutMutation() {
        String key = offerKey("diamond", 2);
        AuthoredStockData stock = AuthoredStockData.createForTest(Map.of(key, 1));

        assertFalse(stock.canPurchase(key, 2));
        assertFalse(stock.recordPurchase(key, 2));
        assertEquals(1, stock.remaining(key));
    }

    @Test void completedPurchaseIsPersistedAndStaleMenuCheckSeesExhaustion() {
        String key = offerKey("diamond", 2);
        AuthoredStockData stock = AuthoredStockData.createForTest(Map.of(key, 2));

        assertTrue(stock.canPurchase(key, 2));
        assertTrue(stock.recordPurchase(key, 2));
        assertFalse(stock.canPurchase(key, 2));
        AuthoredStockData reloaded = AuthoredStockData.load(stock.save(new CompoundTag()));
        assertFalse(reloaded.canPurchase(key, 2));
        assertEquals(0, reloaded.remaining(key));
    }

    @Test void unseededManagedCommodityFailsClosedWithoutTouchingOtherBalances() {
        AuthoredStockData stock = AuthoredStockData.createForTest(Map.of("minecraft:diamond", 4));

        assertFalse(stock.canPurchase("minecraft:netherite_ingot", 1));
        assertFalse(stock.recordPurchase("minecraft:netherite_ingot", 1));
        assertTrue(stock.canPurchase("minecraft:bread", 1));
        assertEquals(0, stock.remaining("minecraft:netherite_ingot"));
        assertEquals(4, stock.remaining("minecraft:diamond"));
    }

    @Test void legacyOfferBalancesMigrateAndCoalesceByPhysicalOutput() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("minecraft:emerald#9:unknown#0=>minecraft:diamond#1", 2);
        legacy.putInt("better_spirit_commerce:tempo_spirit#2:unknown#0=>minecraft:diamond#1", 3);
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        root.put("stock", legacy);

        AuthoredStockData migrated = AuthoredStockData.load(root);

        assertEquals(5, migrated.remaining("minecraft:diamond"));
        AuthoredStockData reloaded = AuthoredStockData.load(migrated.save(new CompoundTag()));
        assertEquals(5, reloaded.remaining("minecraft:diamond"));
        assertTrue(reloaded.canPurchase("minecraft:diamond", 1));
    }

    @Test void versionTwoSeedsNewImportsOnceWithoutRefillingExistingOrMissingLegacyGoods() {
        CompoundTag balances = new CompoundTag();
        balances.putInt("minecraft:diamond", 0);
        balances.putInt("minecraft:diamond_helmet", 2);
        CompoundTag old = new CompoundTag();
        old.putInt("version", 2);
        old.put("stock", balances);
        AuthoredStockData migrated = AuthoredStockData.load(old);
        assertEquals(0, migrated.remaining("minecraft:diamond"));
        assertEquals(2, migrated.remaining("minecraft:diamond_helmet"));
        assertFalse(migrated.canPurchase("minecraft:netherite_ingot", 1));
        int milk = AuthoredStockPolicy.initialStockUnits().get("minecraft:milk_bucket");
        assertEquals(milk, migrated.remaining("minecraft:milk_bucket"));
        assertTrue(migrated.recordPurchase("minecraft:milk_bucket", milk));
        AuthoredStockData reloaded = AuthoredStockData.load(migrated.save(new CompoundTag()));
        assertEquals(0, reloaded.remaining("minecraft:milk_bucket"));
        assertEquals(0, reloaded.remaining("minecraft:diamond"));
        assertEquals(2, reloaded.remaining("minecraft:diamond_helmet"));
        assertEquals(3, reloaded.save(new CompoundTag()).getInt("version"));
    }

    @Test void versionThreeMissingKnownImportsFailClosedAcrossReload() {
        CompoundTag incomplete = new CompoundTag();
        incomplete.putInt("version", 3);
        incomplete.put("stock", new CompoundTag());
        AuthoredStockData loaded = AuthoredStockData.load(incomplete);
        assertEquals(0, loaded.remaining("minecraft:milk_bucket"));
        assertFalse(loaded.canPurchase("minecraft:milk_bucket", 1));
        assertFalse(loaded.recordPurchase("minecraft:milk_bucket", 1));
        AuthoredStockData reloaded = AuthoredStockData.load(loaded.save(new CompoundTag()));
        assertEquals(0, reloaded.remaining("minecraft:milk_bucket"));
        assertFalse(reloaded.canPurchase("minecraft:milk_bucket", 1));
    }

    @Test void versionOnePreservesExplicitlyExhaustedCommodityKeys() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("minecraft:emerald#9:unknown#0=>minecraft:diamond#1", 0);
        assertTrue(AuthoredStockLedger.migrateOfferKeys(legacy).contains("minecraft:diamond"));
        CompoundTag old = new CompoundTag();
        old.putInt("version", 1);
        old.put("stock", legacy);
        AuthoredStockData migrated = AuthoredStockData.load(old);
        assertEquals(0, migrated.remaining("minecraft:diamond"));
        assertFalse(migrated.canPurchase("minecraft:diamond", 1));
    }

    private static String offerKey(final String result, final int count) {
        return AuthoredOfferStock.key(new ResourceLocation("minecraft", result));
    }
}

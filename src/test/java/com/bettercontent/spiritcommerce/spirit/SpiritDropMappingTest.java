package com.bettercontent.spiritcommerce.spirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class SpiritDropMappingTest {
    private static CurrencyIdentity nativeIdentity(Item item) {
        if (item == Items.BLAZE_POWDER || item == Items.REDSTONE) return CurrencyIdentity.POWER;
        return null;
    }

    private static Item commerceItem(CurrencyIdentity identity) {
        return identity == CurrencyIdentity.POWER ? Items.LAPIS_LAZULI : Items.COAL;
    }

    @Test
    void convergingMalumNativeTypesSumIntoOneCoreReagent() {
        List<ItemStack> drops = SpiritAcquisition.mapDrops(List.of(
                new ItemStack(Items.BLAZE_POWDER, 2), new ItemStack(Items.REDSTONE, 3)),
                SpiritDropMappingTest::nativeIdentity, SpiritDropMappingTest::commerceItem);
        assertEquals(1, drops.size());
        assertTrue(drops.get(0).is(Items.LAPIS_LAZULI));
        assertEquals(5, drops.get(0).getCount());
    }

    @Test
    void passesThroughExoticAndAlreadyCommerceResultsIncludingTags() {
        ItemStack exotic = new ItemStack(Items.ECHO_SHARD, 2);
        exotic.getOrCreateTag().putInt("Purity", 3);
        ItemStack commerce = new ItemStack(Items.LAPIS_LAZULI, 4);
        List<ItemStack> drops = SpiritAcquisition.mapDrops(List.of(exotic, commerce),
                SpiritDropMappingTest::nativeIdentity, SpiritDropMappingTest::commerceItem);
        assertEquals(2, drops.size());
        assertTrue(ItemStack.isSameItemSameTags(exotic, drops.get(0)));
        assertEquals(2, drops.get(0).getCount());
        assertTrue(ItemStack.isSameItemSameTags(commerce, drops.get(1)));
        assertEquals(4, drops.get(1).getCount());
        assertFalse(drops.get(0) == exotic);
    }

    @Test
    void alreadyCoreAndRawNativeSourcesMergeWithoutDuplicatingOrErasingRitualTags() {
        ItemStack ritual = new ItemStack(Items.REDSTONE, 2);
        ritual.getOrCreateTag().putString("stored_ritual", "malum:purification");
        ItemStack exotic = new ItemStack(Items.ECHO_SHARD, 3);
        List<ItemStack> drops = SpiritAcquisition.mapDrops(List.of(
                new ItemStack(Items.BLAZE_POWDER, 2), new ItemStack(Items.LAPIS_LAZULI, 3),
                ritual, exotic),
                item -> item == Items.LAPIS_LAZULI ? CurrencyIdentity.POWER : nativeIdentity(item),
                SpiritDropMappingTest::commerceItem);
        assertEquals(3, drops.size());
        assertTrue(drops.get(0).is(Items.LAPIS_LAZULI));
        assertEquals(5, drops.get(0).getCount());
        assertTrue(drops.get(1).is(Items.LAPIS_LAZULI));
        assertEquals(2, drops.get(1).getCount());
        assertEquals("malum:purification", drops.get(1).getTag().getString("stored_ritual"));
        assertTrue(ItemStack.isSameItemSameTags(exotic, drops.get(2)));
    }

    @Test
    void emptyMalumResultCreatesNoSyntheticDrops() {
        assertTrue(SpiritAcquisition.mapDrops(List.of(), SpiritDropMappingTest::nativeIdentity,
                SpiritDropMappingTest::commerceItem).isEmpty());
    }

    @Test
    void dividesLargeCountsIntoValidStacksWithoutLosingValue() {
        List<ItemStack> drops = SpiritAcquisition.mapDrops(List.of(new ItemStack(Items.BLAZE_POWDER, 130)),
                SpiritDropMappingTest::nativeIdentity, SpiritDropMappingTest::commerceItem);
        assertEquals(List.of(64, 64, 2), drops.stream().map(ItemStack::getCount).toList());
        assertTrue(drops.stream().allMatch(stack -> stack.is(Items.LAPIS_LAZULI)));
    }
}

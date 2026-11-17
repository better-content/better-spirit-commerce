package com.bettercontent.spiritcommerce.spirit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class SpiritPickupStackingTest {
    @Test
    void groupedSpiritsBecomeOrdinaryStackableItemsAfterPickup() {
        ItemStack first = grouped();
        ItemStack second = grouped();
        SpiritAcquisition.removeGroupedPickupMarker(first);
        SpiritAcquisition.removeGroupedPickupMarker(second);

        assertFalse(first.hasTag());
        assertTrue(ItemStack.isSameItemSameTags(first, second));
        assertTrue(ItemStack.isSameItemSameTags(first, new ItemStack(Items.BLAZE_POWDER)));
    }

    @Test
    void pickupPreservesUnrelatedTagsAndUnmarkedStacks() {
        ItemStack grouped = grouped();
        grouped.getOrCreateTag().putInt("Purity", 2);
        SpiritAcquisition.removeGroupedPickupMarker(grouped);
        assertTrue(grouped.getTag().contains("Purity"));
        assertFalse(grouped.getTag().contains(SpiritAcquisition.GROUPED_PICKUP_MARKER));

        ItemStack unmarked = new ItemStack(Items.BLAZE_POWDER);
        unmarked.getOrCreateTag().putInt("Purity", 3);
        SpiritAcquisition.removeGroupedPickupMarker(unmarked);
        assertTrue(unmarked.getTag().getInt("Purity") == 3);
    }

    private static ItemStack grouped() {
        ItemStack stack = new ItemStack(Items.BLAZE_POWDER);
        stack.getOrCreateTag().putBoolean(SpiritAcquisition.GROUPED_PICKUP_MARKER, true);
        return stack;
    }
}

package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.npc.VillagerTrades;
import org.junit.jupiter.api.Test;

final class VillagerCatalogueTest {
    @Test void catalogueHasThirtyFiveRowsForEverySpiritProfession() {
        assertEquals(280, VillagerCatalogue.rowCount());
        assertTrue(VillagerCatalogue.allRowsUseExactlyOneSpiritSide());
    }

    @Test void nonEconomyProfessionPreservesExistingListings() {
        Map<Integer, List<VillagerTrades.ItemListing>> trades = new HashMap<>();
        VillagerTrades.ItemListing sentinel = (entity, random) -> null;
        trades.put(1, new ArrayList<>(List.of(sentinel)));

        assertFalse(VillagerCatalogue.replaceListings(null, trades));

        assertEquals(1, trades.size());
        assertEquals(List.of(sentinel), trades.get(1));
    }

    @Test void authoredReplacementKeepsAllGoodsForFiveMergedPaymentProfessions() {
        assertEquals(5, SpiritKind.values().length);
        for (SpiritKind kind : SpiritKind.values()) {
            Map<Integer, List<VillagerTrades.ItemListing>> trades = new HashMap<>();
            VillagerTrades.ItemListing sentinel = (entity, random) -> null;
            trades.put(1, new ArrayList<>(List.of(sentinel)));

            assertTrue(VillagerCatalogue.replaceListings(kind, trades), kind.id());

            assertFalse(trades.values().stream().anyMatch(listings -> listings.contains(sentinel)), kind.id());
            int sources = switch (kind) {
                case POWER, MOTION, ENDURANCE -> 2;
                case RENEWAL, CONTROL -> 1;
            };
            assertEquals(35 * sources + 1, trades.values().stream().mapToInt(List::size).sum(), kind.id());
        }
    }
}

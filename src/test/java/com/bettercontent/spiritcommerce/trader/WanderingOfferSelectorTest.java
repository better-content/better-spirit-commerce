package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.*;
import com.bettercontent.spiritcommerce.config.WanderingPool;
import com.bettercontent.spiritcommerce.config.WanderingPool.Bucket;
import com.bettercontent.spiritcommerce.config.WanderingPool.Row;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class WanderingOfferSelectorTest {
    @Test void everyCompleteThemePicksExactlyFourFourThreeOneAndFireWithoutDuplicates() {
        for (SpiritKind kind : SpiritKind.values()) {
            List<Row> pool = WanderingPool.rows(kind);
            Set<String> seen = new HashSet<>();
            Set<List<String>> baskets = new HashSet<>();
            for (int seed = 0; seed < 512; seed++) {
                UUID uuid = new UUID(seed * 0x9E3779B97F4A7C15L, seed * 0xBF58476D1CE4E5B9L);
                List<Row> selected = WanderingOfferSelector.select(pool, uuid, row -> true);
                assertEquals(13, selected.size(), kind.toString());
                assertEquals(13, selected.stream().map(row -> row.result().id()).distinct().count());
                assertTrue(selected.stream().allMatch(row -> row.kind() == kind));
                for (Bucket bucket : Bucket.values()) assertEquals(bucket.picks(),
                        selected.stream().filter(row -> row.bucket() == bucket).count(), kind + " " + bucket);
                assertEquals(selected, WanderingOfferSelector.select(pool, UUID.fromString(uuid.toString()), row -> true));
                selected.forEach(row -> seen.add(row.id()));
                baskets.add(selected.stream().map(Row::id).toList());
            }
            assertEquals(pool.size(), seen.size(), "Every authored offer must be reachable: " + kind);
            assertTrue(baskets.size() > 100, "Different traders need real assortment variation: " + kind);
        }
    }

    @Test void inputIterationOrderDoesNotRerollTheSavedUuidSelection() {
        UUID uuid = UUID.fromString("12345678-1234-5678-9876-123456789abc");
        List<Row> pool = WanderingPool.rows(SpiritKind.ARCANE);
        List<Row> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, new Random(99));
        assertEquals(WanderingOfferSelector.select(pool, uuid, row -> true),
                WanderingOfferSelector.select(shuffled, uuid, row -> true));
    }

    @Test void optionalMissingContentIsSkippedAndFilledOnlyFromTheSameRegisteredPool() {
        List<Row> pool = WanderingPool.rows(SpiritKind.SACRED);
        // Deliberately starve the equipment bucket: fallback must still fill 12 goods.
        Set<String> available = new HashSet<>(pool.stream()
                .filter(row -> row.bucket() != Bucket.EQUIPMENT && row.bucket() != Bucket.CAMPFIRE)
                .limit(12).map(Row::id).toList());
        pool.stream().filter(row -> row.bucket() == Bucket.CAMPFIRE).forEach(row -> available.add(row.id()));
        var selected = WanderingOfferSelector.select(pool, new UUID(5, 6), row -> available.contains(row.id()));
        assertEquals(13, selected.size());
        assertEquals(13, selected.stream().map(Row::id).distinct().count());
        assertTrue(selected.stream().allMatch(row -> available.contains(row.id())));
    }

    @Test void tinyOptionalProfileNeverFabricatesItemsOrDuplicatesToReachThirteen() {
        var pool = WanderingPool.rows(SpiritKind.SACRED);
        var selected = WanderingOfferSelector.select(pool, new UUID(5, 6),
                row -> row.bucket() == Bucket.CAMPFIRE || row.id().equals("renewal-05"));
        assertEquals(2, selected.size());
        assertEquals(Set.of("renewal-05", "renewal-36"),
                selected.stream().map(Row::id).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void changingThemeAfterJoinUsesTheActualSelectedThemeNotTheUuidFallback() {
        UUID uuid = new UUID(1, 2);
        var first = WanderingOfferSelector.select(WanderingPool.rows(SpiritKind.INFERNAL), uuid, row -> true);
        var actual = WanderingOfferSelector.select(WanderingPool.rows(SpiritKind.AQUEOUS), uuid, row -> true);
        assertTrue(first.stream().allMatch(row -> row.kind() == SpiritKind.INFERNAL));
        assertTrue(actual.stream().allMatch(row -> row.kind() == SpiritKind.AQUEOUS));
        assertNotEquals(first, actual);
        assertEquals(actual, WanderingOfferSelector.select(WanderingPool.rows(SpiritKind.AQUEOUS), uuid, row -> true));
    }

    @Test void rejectsMixedIdentityPoolsAndDuplicateOutputs() {
        var pool = new ArrayList<>(WanderingPool.rows(SpiritKind.ARCANE));
        pool.add(WanderingPool.rows(SpiritKind.AERIAL).get(0));
        assertThrows(IllegalArgumentException.class,
                () -> WanderingOfferSelector.select(pool, new UUID(0, 0), row -> true));
        var duplicate = new ArrayList<>(WanderingPool.rows(SpiritKind.ARCANE));
        duplicate.add(duplicate.get(0));
        assertThrows(IllegalArgumentException.class,
                () -> WanderingOfferSelector.select(duplicate, new UUID(0, 0), row -> true));
    }
}

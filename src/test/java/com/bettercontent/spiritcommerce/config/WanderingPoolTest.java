package com.bettercontent.spiritcommerce.config;

import static org.junit.jupiter.api.Assertions.*;
import com.bettercontent.spiritcommerce.config.WanderingPool.Bucket;
import com.bettercontent.spiritcommerce.config.WanderingPool.Row;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class WanderingPoolTest {
    @Test void fullAuthoredPoolHasThirtyFiveGoodsAndOneMatchingFireForEveryIdentity() {
        assertEquals(288, WanderingPool.rowCount());
        var all = new ArrayList<Row>();
        for (SpiritKind kind : SpiritKind.values()) {
            var rows = WanderingPool.rows(kind);
            all.addAll(rows);
            assertEquals(36, rows.size());
            assertEquals(4, rows.stream().filter(row -> row.bucket() == Bucket.STAPLE).count());
            var fire = rows.stream().filter(row -> row.bucket() == Bucket.CAMPFIRE).findFirst().orElseThrow();
            assertEquals("better_spirit_commerce:" + kind.id() + "_campfire", fire.result().id());
            assertEquals(1, fire.result().count());
            assertEquals(1, fire.cost());
            assertEquals(16, fire.maxUses());
            assertTrue(rows.stream().allMatch(row -> row.cost() >= 1 && row.cost() <= 64));
        }
        assertEquals(288, all.stream().map(row -> row.result().id()).distinct().count());
        assertEquals(204, all.stream().filter(row -> !row.result().id().startsWith("minecraft:")).count());
        assertTrue(all.stream().anyMatch(row -> row.cost() == 64));
        assertTrue(all.stream().noneMatch(row -> row.result().id().startsWith("service:")));
        Set<String> excluded = Set.of("minecraft:elytra", "rehooked:wood_hook", "minecraft:potion",
                "minecraft:enchanted_book", "minecraft:tipped_arrow", "minecraft:filled_map",
                "tconstruct:repair_kit", "additionalweaponry:wrench", "create:mechanical_crafter");
        assertTrue(all.stream().noneMatch(row -> excluded.contains(row.result().id())));
    }

    @Test void originalGlobalCommodityCeilingsRemainExactlySixtyFourUnits() {
        var prior = WanderingPool.commodityStocks().stream()
                .filter(stock -> stock.scope() == WanderingPool.StockScope.ALL_MERCHANTS).toList();
        assertEquals(22, prior.size());
        assertEquals(64, prior.stream().mapToInt(WanderingPool.CommodityStock::units).sum());
        assertTrue(WanderingPool.commodityStocks().stream().filter(stock ->
                stock.scope() == WanderingPool.StockScope.SPIRIT_SALES).allMatch(stock -> stock.units() > 0));
    }

    private WanderingPool.Document document() {
        var rows = java.util.Arrays.stream(SpiritKind.values()).flatMap(kind -> WanderingPool.rows(kind).stream()).toList();
        return new WanderingPool.Document(WanderingPool.SCHEMA, rows, WanderingPool.commodityStocks());
    }

    @Test void rejectsDuplicateRowIdsOutputsInvalidPricesAndMissingFiniteStock() {
        var original = document();
        var rows = new ArrayList<>(original.rows());
        rows.set(1, rows.get(0));
        assertThrows(IllegalArgumentException.class, () -> WanderingPool.validate(
                new WanderingPool.Document(WanderingPool.SCHEMA, rows, original.commodityStocks())));
        var badPrice = new ArrayList<>(original.rows());
        Row row = badPrice.get(0);
        badPrice.set(0, new Row(row.id(), row.spirit(), row.bucket(), 65,
                row.result(), row.maxUses(), row.xp(), row.renewable()));
        assertThrows(IllegalArgumentException.class, () -> WanderingPool.validate(
                new WanderingPool.Document(WanderingPool.SCHEMA, badPrice, original.commodityStocks())));
        assertThrows(IllegalArgumentException.class, () -> WanderingPool.validate(
                new WanderingPool.Document(WanderingPool.SCHEMA, original.rows(), List.of())));
    }

    @Test void rejectsUnknownThemeInsteadOfSilentlyDefaultingAndGuardsFixedStaples() {
        var original = document();
        var badTheme = new ArrayList<>(original.rows());
        Row row = badTheme.get(0);
        badTheme.set(0, new Row(row.id(), "made_up", row.bucket(), row.cost(),
                row.result(), row.maxUses(), row.xp(), row.renewable()));
        assertThrows(IllegalArgumentException.class, () -> WanderingPool.validate(
                new WanderingPool.Document(WanderingPool.SCHEMA, badTheme, original.commodityStocks())));
        var badBucket = new ArrayList<>(original.rows());
        int staple = java.util.stream.IntStream.range(0, badBucket.size())
                .filter(i -> badBucket.get(i).bucket() == Bucket.STAPLE).findFirst().orElseThrow();
        Row s = badBucket.get(staple);
        badBucket.set(staple, new Row(s.id(), s.spirit(), Bucket.SUPPLY, s.cost(),
                s.result(), s.maxUses(), s.xp(), s.renewable()));
        assertThrows(IllegalArgumentException.class, () -> WanderingPool.validate(
                new WanderingPool.Document(WanderingPool.SCHEMA, badBucket, original.commodityStocks())));
    }
}

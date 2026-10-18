package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.*;

import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

final class SpiritCollectionHistoryTest {
    private static final UUID PLAYER = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);

    @Test
    void historyIsPersonalPersistentAndSquareRootDampened() {
        SpiritCollectionHistory data = new SpiritCollectionHistory();
        data.record(PLAYER, CurrencyIdentity.RENEWAL, 100, 0);
        data.record(PLAYER, CurrencyIdentity.CONTROL, 1, 0);
        data.record(OTHER, CurrencyIdentity.IMPACT, 400, 0);
        SpiritCollectionHistory loaded = SpiritCollectionHistory.load(data.save(new CompoundTag()));
        double[] weights = loaded.weights(PLAYER, 0);
        assertEquals(10, weights[CurrencyIdentity.RENEWAL.ordinal()]);
        assertEquals(1, weights[CurrencyIdentity.CONTROL.ordinal()]);
        assertEquals(0, weights[CurrencyIdentity.IMPACT.ordinal()]);
        assertEquals(20, loaded.weights(OTHER, 0)[CurrencyIdentity.IMPACT.ordinal()]);
    }

    @Test
    void tenGameDayBucketsExpireAndNewActivityTakesOver() {
        SpiritCollectionHistory data = new SpiritCollectionHistory();
        data.record(PLAYER, CurrencyIdentity.RENEWAL, 100, 0);
        long lastRecentDay = (SpiritCollectionHistory.RECENT_DAYS - 1) * SpiritCollectionHistory.DAY_TICKS;
        assertEquals(10, data.weights(PLAYER, lastRecentDay)[CurrencyIdentity.RENEWAL.ordinal()]);
        long expired = SpiritCollectionHistory.RECENT_DAYS * SpiritCollectionHistory.DAY_TICKS;
        assertEquals(0, data.weights(PLAYER, expired)[CurrencyIdentity.RENEWAL.ordinal()]);
        data.record(PLAYER, CurrencyIdentity.CONTROL, 9, expired);
        SpiritCollectionHistory loaded = SpiritCollectionHistory.load(data.save(new CompoundTag()));
        assertEquals(0, loaded.weights(PLAYER, expired)[CurrencyIdentity.RENEWAL.ordinal()]);
        assertEquals(3, loaded.weights(PLAYER, expired)[CurrencyIdentity.CONTROL.ordinal()]);
        assertEquals(0, loaded.weights(PLAYER, 0)[CurrencyIdentity.CONTROL.ordinal()], "Future buckets cannot influence a restored clock");
    }

    @Test
    void unknownCurrenciesAndInvalidQuantitiesDoNotBecomeHistory() {
        SpiritCollectionHistory data = new SpiritCollectionHistory();
        data.record(PLAYER, null, 100, 0);
        data.record(PLAYER, CurrencyIdentity.TEMPO, 0, 0);
        data.record(PLAYER, CurrencyIdentity.TEMPO, -1, 0);
        data.record(PLAYER, CurrencyIdentity.TEMPO, 1, -1);
        assertTrue(data.save(new CompoundTag()).getList("Players", 10).isEmpty());
    }

    @Test
    void absentHistoryIsUniformAcrossAllEightTraders() {
        SpiritCollectionHistory data = new SpiritCollectionHistory();
        RandomSource random = RandomSource.create(77123L);
        int[] counts = new int[WanderingTraderTheme.values().length];
        for (int i = 0; i < 80_000; i++) counts[data.selectTheme(PLAYER, 0, random).ordinal()]++;
        for (int count : counts) assertEquals(10_000, count, 400);
    }

    @Test
    void collectedThemeDominatesButRandomVisitorsIncludeEveryUncollectedTheme() {
        SpiritCollectionHistory data = new SpiritCollectionHistory();
        data.record(PLAYER, CurrencyIdentity.RENEWAL, 64, 0);
        RandomSource random = RandomSource.create(12109L);
        int[] counts = new int[WanderingTraderTheme.values().length];
        for (int i = 0; i < 100_000; i++) counts[data.selectTheme(PLAYER, 0, random).ordinal()]++;
        // 85% weighted + 15% / 8 uniform, not a 15% forced different visitor.
        assertEquals(86_875, counts[WanderingTraderTheme.SACRED.ordinal()], 500);
        for (WanderingTraderTheme theme : WanderingTraderTheme.values()) {
            if (theme != WanderingTraderTheme.SACRED) assertEquals(1_875, counts[theme.ordinal()], 250);
        }
    }

    @Test
    void weightedBranchUsesDampenedCountsAndMatchesCurrencyRatherThanEnumOrder() {
        SpiritCollectionHistory data = new SpiritCollectionHistory();
        data.record(PLAYER, CurrencyIdentity.IMPACT, 100, 0);
        data.record(PLAYER, CurrencyIdentity.TEMPO, 1, 0);
        RandomSource random = RandomSource.create(9918L);
        int infernal = 0;
        int tempo = 0;
        for (int i = 0; i < 100_000; i++) {
            WanderingTraderTheme theme = data.selectTheme(PLAYER, 0, random);
            if (theme == WanderingTraderTheme.INFERNAL) infernal++;
            if (theme == WanderingTraderTheme.TEMPO) tempo++;
        }
        assertEquals(100_000 * (0.85 * 10 / 11 + 0.15 / 8), infernal, 500);
        assertEquals(100_000 * (0.85 / 11 + 0.15 / 8), tempo, 500);
    }

    @Test
    void unaffordableThemesNeverLeakIntoEitherSelectionBranch() {
        SpiritCollectionHistory data = new SpiritCollectionHistory();
        data.record(PLAYER, CurrencyIdentity.RENEWAL, 100, 0);
        data.record(PLAYER, CurrencyIdentity.WORK, 1, 0);
        RandomSource random = RandomSource.create(1919L);
        var affordable = java.util.List.of(WanderingTraderTheme.ARCANE, WanderingTraderTheme.TEMPO);
        boolean sawRandomTempo = false;
        for (int i = 0; i < 10_000; i++) {
            var theme = data.selectTheme(PLAYER, 0, random, affordable);
            assertTrue(affordable.contains(theme));
            if (theme == WanderingTraderTheme.TEMPO) sawRandomTempo = true;
        }
        assertTrue(sawRandomTempo, "Random branch must admit an affordable theme absent from collection history");
        assertThrows(IllegalArgumentException.class,
                () -> data.selectTheme(PLAYER, 0, random, java.util.List.of()));
    }

    @Test
    void storageRetainsOnlyTenDailyBucketsPerPlayer() {
        SpiritCollectionHistory data = new SpiritCollectionHistory();
        for (int day = 0; day < 100; day++) {
            data.record(PLAYER, CurrencyIdentity.WORK, 1, day * SpiritCollectionHistory.DAY_TICKS);
        }
        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(10, saved.getList("Players", 10).getCompound(0).getList("Days", 10).size());
        assertEquals(Math.sqrt(10), data.weights(PLAYER, 99 * SpiritCollectionHistory.DAY_TICKS)[CurrencyIdentity.WORK.ordinal()]);
    }
}

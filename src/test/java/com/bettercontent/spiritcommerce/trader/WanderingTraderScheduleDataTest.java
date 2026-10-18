package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class WanderingTraderScheduleDataTest {
    @Test
    void initializesAfterFiveDaysAndRepeatsNineDaysAfterSuccess() {
        final WanderingTraderScheduleData schedule = new WanderingTraderScheduleData();
        schedule.initializeIfNeeded(100L, WanderingTraderVisits.MIN_INTERVAL_TICKS);
        schedule.initializeIfNeeded(200L, 0); // Rechecks must not renew or shorten the initial wait.

        assertFalse(schedule.isVisitDue(120_099L));
        assertTrue(schedule.isVisitDue(120_100L));
        assertEquals(WanderingTraderTheme.SACRED, schedule.nextTheme());
        assertEquals(CurrencyIdentity.RENEWAL, schedule.nextCurrencyIdentity());

        final UUID traderId = UUID.fromString("00000000-0000-0000-0000-000000000019");
        schedule.completeVisit(120_100L, WanderingTraderVisits.MAX_INTERVAL_TICKS, traderId);
        assertEquals(336_100L, schedule.nextAttemptGameTime());
        assertEquals(traderId, schedule.activeTraderId());
        assertEquals(WanderingTraderTheme.WICKED, schedule.nextTheme());
        assertEquals(CurrencyIdentity.CONTROL, schedule.nextCurrencyIdentity());
    }

    @Test
    void retryDoesNotAdvanceThemeAndSavedStateRoundTrips() {
        final WanderingTraderScheduleData schedule = new WanderingTraderScheduleData();
        schedule.initializeIfNeeded(0L, 48_000);
        schedule.scheduleRetry(48_000L, 1_200);

        final WanderingTraderScheduleData loaded = WanderingTraderScheduleData.load(schedule.save(new CompoundTag()));
        assertEquals(49_200L, loaded.nextAttemptGameTime());
        assertEquals(WanderingTraderTheme.SACRED, loaded.nextTheme());
        assertEquals(CurrencyIdentity.RENEWAL, loaded.nextCurrencyIdentity());
        assertNull(loaded.activeTraderId());
    }

    @Test
    void intervalRollsStayWithinFiveToNineDaysAndV2TimersAreReinitialized() {
        var random = net.minecraft.util.RandomSource.create(12345L);
        int min = Integer.MAX_VALUE, max = 0;
        for (int i = 0; i < 10000; i++) {
            int interval = WanderingTraderVisits.nextInterval(random);
            assertTrue(interval >= 120_000 && interval <= 216_000);
            min = Math.min(min, interval);
            max = Math.max(max, interval);
        }
        assertTrue(min < 121_000 && max > 215_000);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("SchemaVersion", 2);
        legacy.putBoolean("Initialized", true);
        legacy.putLong("NextAttemptGameTime", Long.MAX_VALUE);
        var migrated = WanderingTraderScheduleData.load(legacy);
        assertFalse(migrated.initialized());
        migrated.initializeIfNeeded(500, 120_000);
        var loaded = WanderingTraderScheduleData.load(migrated.save(new CompoundTag()));
        assertTrue(loaded.initialized());
        assertEquals(120_500, loaded.nextAttemptGameTime());
        loaded.clearActiveTrader();
        assertEquals(120_500, loaded.nextAttemptGameTime());
        assertEquals(168_000, WanderingTraderVisits.VISIT_TICKS);
    }

    @Test
    void themeCycleIsStable() {
        assertEquals(WanderingTraderTheme.WICKED, WanderingTraderTheme.SACRED.next());
        assertEquals(WanderingTraderTheme.ARCANE, WanderingTraderTheme.WICKED.next());
        assertEquals(WanderingTraderTheme.AERIAL, WanderingTraderTheme.ARCANE.next());
        assertEquals(WanderingTraderTheme.TEMPO, WanderingTraderTheme.INFERNAL.next());
        assertEquals(WanderingTraderTheme.SACRED, WanderingTraderTheme.TEMPO.next());
    }

    @Test
    void schemaOneScheduleDerivesItsCurrencyIdentityWithoutChangingLegacyThemeOrder() {
        final CompoundTag legacy = new CompoundTag();
        legacy.putInt("SchemaVersion", 1);
        legacy.putInt("NextThemeIndex", 2);

        final WanderingTraderScheduleData migrated = WanderingTraderScheduleData.load(legacy);

        assertEquals(WanderingTraderTheme.ARCANE, migrated.nextTheme());
        assertEquals(CurrencyIdentity.WORK, migrated.nextCurrencyIdentity());
        assertEquals("work", migrated.save(new CompoundTag()).getString("NextCurrencyIdentity"));
    }
}

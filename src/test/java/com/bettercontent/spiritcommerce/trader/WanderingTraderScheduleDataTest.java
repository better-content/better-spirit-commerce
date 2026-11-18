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
    void initializesAfterTwoAndAHalfDaysAndRepeatsFourAndAHalfDaysAfterSuccess() {
        final WanderingTraderScheduleData schedule = new WanderingTraderScheduleData();
        schedule.initializeIfNeeded(100L, WanderingTraderVisits.MIN_INTERVAL_TICKS);
        schedule.initializeIfNeeded(200L, 0); // Rechecks must not renew or shorten the initial wait.

        assertFalse(schedule.isVisitDue(60_099L));
        assertTrue(schedule.isVisitDue(60_100L));
        assertEquals(WanderingTraderTheme.POWER, schedule.nextTheme());
        assertEquals(CurrencyIdentity.POWER, schedule.nextCurrencyIdentity());

        final UUID traderId = UUID.fromString("00000000-0000-0000-0000-000000000019");
        schedule.completeVisit(60_100L, WanderingTraderVisits.MAX_INTERVAL_TICKS, traderId);
        assertEquals(168_100L, schedule.nextAttemptGameTime());
        assertEquals(traderId, schedule.activeTraderId());
        assertEquals(WanderingTraderTheme.MOTION, schedule.nextTheme());
        assertEquals(CurrencyIdentity.MOTION, schedule.nextCurrencyIdentity());
    }

    @Test
    void retryDoesNotAdvanceThemeAndSavedStateRoundTrips() {
        final WanderingTraderScheduleData schedule = new WanderingTraderScheduleData();
        schedule.initializeIfNeeded(0L, 48_000);
        schedule.scheduleRetry(48_000L, 1_200);

        final WanderingTraderScheduleData loaded = WanderingTraderScheduleData.load(schedule.save(new CompoundTag()));
        assertEquals(49_200L, loaded.nextAttemptGameTime());
        assertEquals(WanderingTraderTheme.POWER, loaded.nextTheme());
        assertEquals(CurrencyIdentity.POWER, loaded.nextCurrencyIdentity());
        assertNull(loaded.activeTraderId());
    }

    @Test
    void intervalRollsStayWithinTwoAndAHalfToFourAndAHalfDaysAndV2TimersAreReinitialized() {
        var random = net.minecraft.util.RandomSource.create(12345L);
        int min = Integer.MAX_VALUE, max = 0;
        for (int i = 0; i < 10000; i++) {
            int interval = WanderingTraderVisits.nextInterval(random);
            assertTrue(interval >= 60_000 && interval <= 108_000);
            min = Math.min(min, interval);
            max = Math.max(max, interval);
        }
        assertTrue(min < 61_000 && max > 107_000);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("SchemaVersion", 2);
        legacy.putBoolean("Initialized", true);
        legacy.putLong("NextAttemptGameTime", Long.MAX_VALUE);
        var migrated = WanderingTraderScheduleData.load(legacy);
        assertFalse(migrated.initialized());
        migrated.initializeIfNeeded(500, 60_000);
        var loaded = WanderingTraderScheduleData.load(migrated.save(new CompoundTag()));
        assertTrue(loaded.initialized());
        assertEquals(60_500, loaded.nextAttemptGameTime());
        loaded.clearActiveTrader();
        assertEquals(60_500, loaded.nextAttemptGameTime());
        assertEquals(168_000, WanderingTraderVisits.VISIT_TICKS);
    }

    @Test
    void themeCycleIsStable() {
        assertEquals(WanderingTraderTheme.MOTION, WanderingTraderTheme.POWER.next());
        assertEquals(WanderingTraderTheme.ENDURANCE, WanderingTraderTheme.MOTION.next());
        assertEquals(WanderingTraderTheme.RENEWAL, WanderingTraderTheme.ENDURANCE.next());
        assertEquals(WanderingTraderTheme.CONTROL, WanderingTraderTheme.RENEWAL.next());
        assertEquals(WanderingTraderTheme.POWER, WanderingTraderTheme.CONTROL.next());
    }

    @Test
    void schemaTwoSavedFirstOverrideAndNextThemeMigrateWithoutLosingIdentity() {
        final CompoundTag legacy = new CompoundTag();
        legacy.putInt("SchemaVersion", 2);
        legacy.putInt("NextThemeIndex", 7); // tempo in the original eight-theme cycle
        legacy.putString("NextCurrencyIdentity", "tempo");
        legacy.putString("FirstCurrency", "work");
        legacy.putString("FoundersTransaction", "old-visit");
        legacy.putLong("NextAttemptGameTime", 48000);

        var migrated = WanderingTraderScheduleData.load(legacy);
        assertEquals(WanderingTraderTheme.POWER, migrated.nextTheme()); // pending first override
        assertEquals(CurrencyIdentity.POWER, migrated.nextCurrencyIdentity());
        var saved = migrated.save(new CompoundTag());
        assertEquals("power", saved.getString("FirstCurrency"));
        assertEquals("motion", saved.getString("NextCurrencyIdentity"));
        assertEquals(1, saved.getInt("NextThemeIndex"));
        assertEquals(48000, migrated.nextAttemptGameTime());
    }

    @Test
    void schemaOneScheduleDerivesItsCurrencyIdentityWithoutChangingLegacyThemeOrder() {
        final CompoundTag legacy = new CompoundTag();
        legacy.putInt("SchemaVersion", 1);
        legacy.putInt("NextThemeIndex", 2);

        final WanderingTraderScheduleData migrated = WanderingTraderScheduleData.load(legacy);

        assertEquals(WanderingTraderTheme.POWER, migrated.nextTheme());
        assertEquals(CurrencyIdentity.POWER, migrated.nextCurrencyIdentity());
        assertEquals("power", migrated.save(new CompoundTag()).getString("NextCurrencyIdentity"));
    }
}

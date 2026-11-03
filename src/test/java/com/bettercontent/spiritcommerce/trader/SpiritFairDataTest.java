package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class SpiritFairDataTest {
    @Test
    void admissionSurvivesRestartBeforeAndAfterSuccessfulSpawn() {
        SpiritFairData data = new SpiritFairData();
        assertTrue(data.canAttempt(1, 0));
        data.admit(1);
        SpiritFairData admitted = SpiritFairData.load(data.save(new CompoundTag()));
        assertNull(admitted.successfulTrader());
        assertFalse(admitted.canAttempt(1, 100000));
        UUID id = UUID.randomUUID();
        admitted.complete(id);
        SpiritFairData restarted = SpiritFairData.load(admitted.save(new CompoundTag()));
        assertEquals(id, restarted.successfulTrader());
        assertFalse(restarted.canAttempt(1, 100000));
        assertFalse(restarted.canAttempt(0, 100000));
        assertTrue(restarted.canAttempt(2, 100000));
        assertThrows(IllegalStateException.class, () -> restarted.admit(1));
    }

    @Test
    void retriesAreOneMinuteAndDoNotAdvanceThemeOrAdmit() {
        SpiritFairData data = new SpiritFairData();
        data.retryAfter(20);
        SpiritFairData restarted = SpiritFairData.load(data.save(new CompoundTag()));
        assertFalse(restarted.canAttempt(8, 1219));
        assertTrue(restarted.canAttempt(8, 1220));
        assertEquals(WanderingTraderTheme.SACRED, restarted.nextTheme());
        assertNull(restarted.successfulTrader());
    }

    @Test
    void canceledUnexposedInsertionCanRetryWithoutAdvancingThemeOrErasingPreviousSuccess() {
        SpiritFairData data = new SpiritFairData();
        UUID previous = UUID.randomUUID();
        data.admit(1);
        data.complete(previous);
        data.retryAfter(100);
        var canceled = data.admit(2);
        assertEquals(WanderingTraderTheme.WICKED, data.nextTheme());
        data.rollbackUnexposed(canceled);
        SpiritFairData restarted = SpiritFairData.load(data.save(new CompoundTag()));
        assertEquals(previous, restarted.successfulTrader());
        assertFalse(restarted.canAttempt(1, 1300));
        assertFalse(restarted.canAttempt(2, 1299));
        assertTrue(restarted.canAttempt(2, 1300));
        assertEquals(WanderingTraderTheme.WICKED, restarted.nextTheme());
        var successful = restarted.admit(2);
        restarted.complete(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> restarted.rollbackUnexposed(successful));
        assertEquals(WanderingTraderTheme.ARCANE, restarted.nextTheme());
    }

    @Test
    void themeRotationIsDeterministicIndependentOfSpacingOrOrdinarySchedule() {
        SpiritFairData data = new SpiritFairData();
        WanderingTraderScheduleData ordinary = new WanderingTraderScheduleData();
        for (int i = 0; i < 2 * WanderingTraderTheme.values().length; i++) {
            assertEquals(WanderingTraderTheme.fromIndex(i), data.nextTheme());
            data.admit(1 + i * 11L);
            data.complete(UUID.randomUUID());
            data = SpiritFairData.load(data.save(new CompoundTag()));
            assertEquals(WanderingTraderTheme.SACRED, ordinary.nextTheme());
            assertNull(ordinary.activeTraderId());
        }
    }
}

package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FoundersExchangePolicyTest {
    @Test void everyFirstCurrencyKeepsDateAndReturnsToOrdinaryCycleAfterSuccess() {
        for (CurrencyIdentity identity : CurrencyIdentity.values()) {
            WanderingTraderScheduleData data = new WanderingTraderScheduleData();
            data.initializeIfNeeded(100, 48000);
            long due = data.nextAttemptGameTime();
            data.chooseFirst("tx", identity.id());
            assertEquals(identity, data.nextCurrencyIdentity());
            assertEquals(identity, data.nextTheme().currencyIdentity());
            assertEquals(due, data.nextAttemptGameTime());
            data = WanderingTraderScheduleData.load(data.save(new CompoundTag()));
            data.chooseFirst("tx", identity.id());
            assertEquals(identity, data.nextCurrencyIdentity());
            assertEquals(due, data.nextAttemptGameTime());
            data.scheduleRetry(due, 1200);
            assertEquals(identity, data.nextTheme().currencyIdentity());
            data.completeVisit(due + 1200, 120000, UUID.randomUUID());
            assertEquals(WanderingTraderTheme.WICKED, data.nextTheme());
            assertEquals(CurrencyIdentity.CONTROL, data.nextCurrencyIdentity());
            assertEquals(due + 121200, data.nextAttemptGameTime());
            data = WanderingTraderScheduleData.load(data.save(new CompoundTag()));
            data.chooseFirst("tx", identity.id());
            assertEquals(CurrencyIdentity.CONTROL, data.nextCurrencyIdentity(), "Replay must not resurrect first override");
        }
    }
    @Test void invalidOrConflictingOverrideCannotMutateNativeSchedule() {
        WanderingTraderScheduleData data = new WanderingTraderScheduleData();
        data.initializeIfNeeded(0, 48000);
        assertThrows(IllegalArgumentException.class, () -> data.chooseFirst("tx", "not_currency"));
        assertEquals(CurrencyIdentity.RENEWAL, data.nextCurrencyIdentity());
        assertEquals(48000, data.nextAttemptGameTime());
        data.chooseFirst("tx", "impact");
        assertThrows(IllegalStateException.class, () -> data.chooseFirst("other", "tempo"));
        assertEquals(CurrencyIdentity.IMPACT, data.nextCurrencyIdentity());
    }
    @Test void ordinaryDisabledScheduleRemainsUnmodified() {
        WanderingTraderScheduleData data = new WanderingTraderScheduleData();
        data.initializeIfNeeded(0, 48000);
        assertEquals(CurrencyIdentity.RENEWAL, data.nextCurrencyIdentity());
        assertEquals(WanderingTraderTheme.SACRED, data.nextTheme());
        assertEquals("", data.save(new CompoundTag()).getString("FoundersTransaction"));
    }
}

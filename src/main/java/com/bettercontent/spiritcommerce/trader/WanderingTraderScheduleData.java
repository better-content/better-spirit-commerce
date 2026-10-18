package com.bettercontent.spiritcommerce.trader;

import java.util.UUID;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class WanderingTraderScheduleData extends SavedData {
    private static final String DATA_NAME = "better_spirit_commerce_wandering_trader";
    private static final int SCHEMA_VERSION = 3;

    private boolean initialized;
    private long nextAttemptGameTime;
    private int nextThemeIndex;
    private String nextCurrencyIdentity = CurrencyIdentity.RENEWAL.id();
    private UUID activeTraderId;

    public static WanderingTraderScheduleData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                WanderingTraderScheduleData::load,
                WanderingTraderScheduleData::new,
                DATA_NAME);
    }

    public static WanderingTraderScheduleData load(final CompoundTag tag) {
        final WanderingTraderScheduleData data = new WanderingTraderScheduleData();
        // v1/v2 timers were inactive under immediate admission; start a fresh bounded schedule.
        data.initialized = tag.getInt("SchemaVersion") >= SCHEMA_VERSION && tag.getBoolean("Initialized");
        data.nextAttemptGameTime = tag.getLong("NextAttemptGameTime");
        data.nextThemeIndex = tag.getInt("NextThemeIndex");
        if (tag.getInt("SchemaVersion") >= 2) {
            data.nextCurrencyIdentity = tag.getString("NextCurrencyIdentity");
        } else {
            data.nextCurrencyIdentity = WanderingTraderTheme.fromIndex(data.nextThemeIndex).currencyIdentity().id();
        }
        if (tag.hasUUID("ActiveTrader")) {
            data.activeTraderId = tag.getUUID("ActiveTrader");
        }
        return data;
    }

    @Override
    public CompoundTag save(final CompoundTag tag) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        tag.putBoolean("Initialized", initialized);
        tag.putLong("NextAttemptGameTime", nextAttemptGameTime);
        tag.putInt("NextThemeIndex", nextThemeIndex);
        tag.putString("NextCurrencyIdentity", nextCurrencyIdentity);
        if (activeTraderId != null) {
            tag.putUUID("ActiveTrader", activeTraderId);
        }
        return tag;
    }

    public void initializeIfNeeded(final long gameTime, final int initialDelay) {
        if (initialized) {
            return;
        }
        initialized = true;
        nextAttemptGameTime = gameTime + initialDelay;
        setDirty();
    }

    public boolean initialized() {
        return initialized;
    }

    public boolean isVisitDue(final long gameTime) {
        return initialized && gameTime >= nextAttemptGameTime;
    }

    public WanderingTraderTheme nextTheme() {
        return WanderingTraderTheme.fromIndex(nextThemeIndex);
    }

    public CurrencyIdentity nextCurrencyIdentity() {
        for (CurrencyIdentity identity : CurrencyIdentity.values()) {
            if (identity.id().equals(nextCurrencyIdentity)) return identity;
        }
        return nextTheme().currencyIdentity();
    }

    public UUID activeTraderId() {
        return activeTraderId;
    }

    public long nextAttemptGameTime() {
        return nextAttemptGameTime;
    }

    public void scheduleRetry(final long gameTime, final int retryDelay) {
        nextAttemptGameTime = gameTime + retryDelay;
        setDirty();
    }

    public void completeVisit(final long gameTime, final int visitInterval, final UUID traderId) {
        activeTraderId = traderId;
        nextAttemptGameTime = gameTime + visitInterval;
        nextThemeIndex = Math.floorMod(nextThemeIndex + 1, WanderingTraderTheme.values().length);
        nextCurrencyIdentity = nextTheme().currencyIdentity().id();
        setDirty();
    }

    public void clearActiveTrader() {
        if (activeTraderId != null) {
            activeTraderId = null;
            setDirty();
        }
    }
}

package com.bettercontent.spiritcommerce.trader;

import java.util.UUID;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class WanderingTraderScheduleData extends SavedData {
    private static final String DATA_NAME = "better_spirit_commerce_wandering_trader";
    private static final int SCHEMA_VERSION = 2;

    private boolean initialized;
    private long nextAttemptGameTime;
    private int nextThemeIndex;
    private String nextCurrencyIdentity = CurrencyIdentity.RENEWAL.id();
    private UUID activeTraderId;
    private String foundersTransaction = "";
    private String firstCurrency = "";
    private boolean firstVisitCompleted;

    public static WanderingTraderScheduleData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                WanderingTraderScheduleData::load,
                WanderingTraderScheduleData::new,
                DATA_NAME);
    }

    public static WanderingTraderScheduleData load(final CompoundTag tag) {
        final WanderingTraderScheduleData data = new WanderingTraderScheduleData();
        data.foundersTransaction = tag.getString("FoundersTransaction");
        data.firstCurrency = tag.getString("FirstCurrency");
        data.firstVisitCompleted = tag.getBoolean("FirstVisitCompleted");
        data.initialized = tag.getBoolean("Initialized");
        data.nextAttemptGameTime = tag.getLong("NextAttemptGameTime");
        data.nextThemeIndex = tag.getInt("NextThemeIndex");
        if (tag.getInt("SchemaVersion") >= SCHEMA_VERSION) {
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
        tag.putString("FoundersTransaction", foundersTransaction);
        tag.putString("FirstCurrency", firstCurrency);
        tag.putBoolean("FirstVisitCompleted", firstVisitCompleted);
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

    public boolean isVisitDue(final long gameTime) {
        return initialized && gameTime >= nextAttemptGameTime;
    }

    /** Only the first identity is changed: no date, stock, retry or spawning rule bypass. */
    public void chooseFirst(String transaction, String currency) {
        if (transaction == null || transaction.isBlank()) throw new IllegalArgumentException("Empty founders transaction");
        CurrencyIdentity identity = CurrencyIdentity.valueOf(currency.toUpperCase(java.util.Locale.ROOT));
        if (foundersTransaction.equals(transaction)) return;
        if (!foundersTransaction.isEmpty() || firstVisitCompleted || activeTraderId != null)
            throw new IllegalStateException("First trader visit already consumed");
        firstCurrency = identity.id();
        foundersTransaction = transaction;
        setDirty();
    }

    public WanderingTraderTheme nextTheme() {
        if (!firstCurrency.isEmpty()) {
            for (WanderingTraderTheme theme : WanderingTraderTheme.values())
                if (theme.currencyIdentity().id().equals(firstCurrency)) return theme;
            throw new IllegalStateException("Invalid first trader currency");
        }
        return WanderingTraderTheme.fromIndex(nextThemeIndex);
    }

    public CurrencyIdentity nextCurrencyIdentity() {
        if (!firstCurrency.isEmpty()) return nextTheme().currencyIdentity();
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
        firstVisitCompleted = true;
        firstCurrency = "";
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

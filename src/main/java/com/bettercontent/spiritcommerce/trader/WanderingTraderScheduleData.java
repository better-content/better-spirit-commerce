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
    private String nextCurrencyIdentity = CurrencyIdentity.POWER.id();
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
        data.firstCurrency = canonicalCurrency(tag.getString("FirstCurrency"));
        data.firstVisitCompleted = tag.getBoolean("FirstVisitCompleted");
        // v1/v2 timers were inactive under immediate admission; start a fresh bounded schedule.
        data.initialized = tag.getInt("SchemaVersion") >= SCHEMA_VERSION && tag.getBoolean("Initialized");
        data.nextAttemptGameTime = tag.getLong("NextAttemptGameTime");
        int storedIndex = tag.getInt("NextThemeIndex");
        // v1/v2 indices named the old eight-theme rotation. Preserve its next
        // intended identity when moving to the five live themes on load.
        data.nextThemeIndex = tag.getInt("SchemaVersion") < SCHEMA_VERSION
                ? legacyTheme(storedIndex).ordinal() : storedIndex;
        if (tag.getInt("SchemaVersion") >= 2) {
            data.nextCurrencyIdentity = canonicalCurrency(tag.getString("NextCurrencyIdentity"));
        } else {
            data.nextCurrencyIdentity = WanderingTraderTheme.fromIndex(data.nextThemeIndex).currencyIdentity().id();
        }
        if (tag.hasUUID("ActiveTrader")) {
            data.activeTraderId = tag.getUUID("ActiveTrader");
        }
        return data;
    }

    private static String canonicalCurrency(String saved) {
        if (saved.isEmpty()) return saved;
        var kind = com.bettercontent.spiritcommerce.spirit.SpiritKind.fromId(saved);
        return kind == null ? saved : kind.currencyIdentity().id();
    }

    private static WanderingTraderTheme legacyTheme(int index) {
        return switch (Math.floorMod(index, 8)) {
            case 0 -> WanderingTraderTheme.RENEWAL; // sacred
            case 1 -> WanderingTraderTheme.CONTROL; // wicked
            case 2, 6 -> WanderingTraderTheme.POWER; // arcane/infernal
            case 3, 7 -> WanderingTraderTheme.MOTION; // aerial/tempo
            default -> WanderingTraderTheme.ENDURANCE; // aqueous/earthen
        };
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

    public boolean initialized() {
        return initialized;
    }

    public boolean isVisitDue(final long gameTime) {
        return initialized && gameTime >= nextAttemptGameTime;
    }

    /** Only the first identity is changed: no date, stock, retry or spawning rule bypass. */
    public void chooseFirst(String transaction, String currency) {
        if (transaction == null || transaction.isBlank()) throw new IllegalArgumentException("Empty founders transaction");
        var kind = com.bettercontent.spiritcommerce.spirit.SpiritKind.fromId(currency);
        if (kind == null) throw new IllegalArgumentException("Unknown first trader currency " + currency);
        CurrencyIdentity identity = kind.currencyIdentity();
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

    public boolean hasPendingFirstCurrency() {
        return !firstVisitCompleted && !firstCurrency.isEmpty();
    }

    public CurrencyIdentity nextCurrencyIdentity() {
        if (!firstCurrency.isEmpty()) return nextTheme().currencyIdentity();
        var kind = com.bettercontent.spiritcommerce.spirit.SpiritKind.fromId(nextCurrencyIdentity);
        return kind == null ? nextTheme().currencyIdentity() : kind.currencyIdentity();
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

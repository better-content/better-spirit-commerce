package com.bettercontent.spiritcommerce.trader;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Successful/crash-ambiguous admission is terminal; only verified never-inserted cancellation may retry. */
public final class SpiritFairData extends SavedData {
    private long admittedOccurrence = Long.MIN_VALUE;
    private UUID successfulTrader;
    private long nextAttempt;
    private int nextTheme;

    public static SpiritFairData get(final ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                SpiritFairData::load, SpiritFairData::new, "better_spirit_commerce_spirit_fair");
    }

    public static SpiritFairData load(final CompoundTag tag) {
        SpiritFairData data = new SpiritFairData();
        if (tag.contains("AdmittedOccurrence")) data.admittedOccurrence = tag.getLong("AdmittedOccurrence");
        if (tag.hasUUID("SuccessfulTrader")) data.successfulTrader = tag.getUUID("SuccessfulTrader");
        data.nextAttempt = tag.getLong("NextAttempt");
        data.nextTheme = tag.getInt("NextTheme");
        return data;
    }

    @Override
    public CompoundTag save(final CompoundTag tag) {
        tag.putLong("AdmittedOccurrence", admittedOccurrence);
        if (successfulTrader != null) tag.putUUID("SuccessfulTrader", successfulTrader);
        tag.putLong("NextAttempt", nextAttempt);
        tag.putInt("NextTheme", nextTheme);
        return tag;
    }

    public boolean canAttempt(final long occurrence, final long gameTime) {
        return occurrence > admittedOccurrence && gameTime >= nextAttempt;
    }

    public void retryAfter(final long gameTime) {
        nextAttempt = gameTime + 1200L;
        setDirty();
    }

    record Admission(long occurrence, long previousOccurrence, UUID previousSuccessfulTrader) { }

    public Admission admit(final long occurrence) {
        if (occurrence <= admittedOccurrence) throw new IllegalStateException("Fair already admitted");
        Admission admission = new Admission(occurrence, admittedOccurrence, successfulTrader);
        admittedOccurrence = occurrence;
        successfulTrader = null;
        setDirty();
        return admission;
    }

    /** Only a verified canceled, never-inserted spawn may undo its own unsucceeded reservation. */
    void rollbackUnexposed(final Admission admission) {
        if (admittedOccurrence != admission.occurrence() || successfulTrader != null) {
            throw new IllegalStateException("Cannot roll back a completed or superseded Fair admission");
        }
        admittedOccurrence = admission.previousOccurrence();
        successfulTrader = admission.previousSuccessfulTrader();
        // Deliberately preserve the one-minute retry deadline.
        setDirty();
    }

    public void complete(final UUID trader) {
        if (successfulTrader != null) throw new IllegalStateException("Fair already completed");
        successfulTrader = trader;
        nextTheme = Math.floorMod(nextTheme + 1, WanderingTraderTheme.values().length);
        setDirty();
    }

    public WanderingTraderTheme nextTheme() { return WanderingTraderTheme.fromIndex(nextTheme); }
    public UUID successfulTrader() { return successfulTrader; }
}

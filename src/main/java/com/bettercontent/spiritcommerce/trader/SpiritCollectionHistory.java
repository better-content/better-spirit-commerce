package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/** Actual commerce-spirit pickups, independent of inventory contents and spending. */
public final class SpiritCollectionHistory extends SavedData {
    static final int RECENT_DAYS = 10;
    static final long DAY_TICKS = 24_000L;
    static final double RANDOM_VISIT_CHANCE = 0.15;
    private static final String DATA_NAME = "better_spirit_commerce_collection_history";
    private final Map<UUID, TreeMap<Long, long[]>> players = new HashMap<>();

    public static SpiritCollectionHistory get(ServerLevel level) {
        // One clock and store for pickups in every dimension; sleep/daytime changes do not age it.
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                SpiritCollectionHistory::load, SpiritCollectionHistory::new, DATA_NAME);
    }

    public void record(UUID player, CurrencyIdentity identity, int count, long gameTime) {
        if (identity == null || count <= 0 || gameTime < 0) return;
        long day = gameTime / DAY_TICKS;
        TreeMap<Long, long[]> days = players.computeIfAbsent(player, ignored -> new TreeMap<>());
        days.headMap(day - RECENT_DAYS + 1, false).clear();
        long[] counts = days.computeIfAbsent(day, ignored -> new long[CurrencyIdentity.values().length]);
        int index = identity.ordinal();
        counts[index] = counts[index] > Long.MAX_VALUE - count ? Long.MAX_VALUE : counts[index] + count;
        // Bound retained history even if a clock is restored backwards.
        while (days.size() > RECENT_DAYS) days.pollFirstEntry();
        setDirty();
    }

    double[] weights(UUID player, long gameTime) {
        double[] totals = new double[CurrencyIdentity.values().length];
        TreeMap<Long, long[]> days = players.get(player);
        if (days == null || gameTime < 0) return totals;
        long day = gameTime / DAY_TICKS;
        for (long[] counts : days.subMap(day - RECENT_DAYS + 1, true, day, true).values()) {
            for (int i = 0; i < totals.length; i++) totals[i] += counts[i];
        }
        for (int i = 0; i < totals.length; i++) totals[i] = Math.sqrt(totals[i]);
        return totals;
    }

    public WanderingTraderTheme selectTheme(UUID player, long gameTime, RandomSource random) {
        final var eligible = java.util.List.of(WanderingTraderTheme.values());
        double[] weights = weights(player, gameTime);
        double total = 0;
        for (WanderingTraderTheme theme : eligible) total += weights[theme.currencyIdentity().ordinal()];
        if (total == 0 || random.nextDouble() < RANDOM_VISIT_CHANCE) {
            return eligible.get(random.nextInt(eligible.size()));
        }
        double roll = random.nextDouble() * total;
        WanderingTraderTheme last = eligible.get(0);
        for (WanderingTraderTheme theme : eligible) {
            double weight = weights[theme.currencyIdentity().ordinal()];
            if (weight <= 0) continue;
            last = theme;
            roll -= weight;
            if (roll < 0) return theme;
        }
        return last; // Floating-point rounding at the upper endpoint.
    }

    public static SpiritCollectionHistory load(CompoundTag tag) {
        SpiritCollectionHistory data = new SpiritCollectionHistory();
        for (Tag entry : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag) entry;
            if (!player.hasUUID("Player")) continue;
            TreeMap<Long, long[]> days = new TreeMap<>();
            for (Tag dayEntry : player.getList("Days", Tag.TAG_COMPOUND)) {
                CompoundTag day = (CompoundTag) dayEntry;
                if (day.getLong("Day") < 0) continue;
                long[] counts = new long[CurrencyIdentity.values().length];
                for (CurrencyIdentity identity : CurrencyIdentity.values()) {
                    counts[identity.ordinal()] = Math.max(0, day.getLong(identity.id()));
                }
                days.put(day.getLong("Day"), counts);
            }
            while (days.size() > RECENT_DAYS) days.pollFirstEntry();
            if (!days.isEmpty()) data.players.put(player.getUUID("Player"), days);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag playerTags = new ListTag();
        players.forEach((id, days) -> {
            CompoundTag player = new CompoundTag();
            player.putUUID("Player", id);
            ListTag dayTags = new ListTag();
            days.forEach((number, counts) -> {
                CompoundTag day = new CompoundTag();
                day.putLong("Day", number);
                for (CurrencyIdentity identity : CurrencyIdentity.values()) {
                    day.putLong(identity.id(), counts[identity.ordinal()]);
                }
                dayTags.add(day);
            });
            player.put("Days", dayTags);
            playerTags.add(player);
        });
        tag.put("Players", playerTags);
        return tag;
    }
}

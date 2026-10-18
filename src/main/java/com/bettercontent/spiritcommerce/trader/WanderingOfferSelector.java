package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.config.WanderingPool;
import com.bettercontent.spiritcommerce.config.WanderingPool.Bucket;
import com.bettercontent.spiritcommerce.config.WanderingPool.Row;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/** Uniform sampling within authored buckets, stable from the trader's natively saved UUID. */
public final class WanderingOfferSelector {
    private WanderingOfferSelector() {}

    public static List<Row> select(final List<Row> pool, final UUID traderId,
                                    final Predicate<Row> available) {
        if (pool.isEmpty()) return List.of();
        String theme = pool.get(0).spirit();
        if (pool.stream().anyMatch(row -> !theme.equals(row.spirit()))
                || pool.stream().map(Row::id).distinct().count() != pool.size()
                || pool.stream().map(row -> row.result().id()).distinct().count() != pool.size()) {
            throw new IllegalArgumentException("Selection requires one identity with distinct rows and outputs");
        }
        Random random = new Random(traderId.getMostSignificantBits()
                ^ Long.rotateLeft(traderId.getLeastSignificantBits(), 23)
                ^ ((long) theme.hashCode() << 32) ^ 0x5350495249540001L);
        List<Row> registered = pool.stream().filter(available)
                .sorted(Comparator.comparing(Row::id)).toList();
        List<Row> result = new ArrayList<>();
        HashSet<String> chosen = new HashSet<>();
        for (Bucket bucket : List.of(Bucket.STAPLE, Bucket.SUPPLY, Bucket.EQUIPMENT, Bucket.STANDOUT)) {
            List<Row> candidates = new ArrayList<>(registered.stream()
                    .filter(row -> row.bucket() == bucket).toList());
            if (bucket != Bucket.STAPLE) Collections.shuffle(candidates, random);
            for (Row row : candidates.stream().limit(bucket.picks()).toList()) {
                result.add(row);
                chosen.add(row.id());
            }
        }
        // Commerce also supports smaller optional-mod profiles. Fill a missing category from
        // other registered goods, never mint a replacement item or include an absent/air output.
        if (result.size() < WanderingPool.GOODS_PER_VISIT) {
            List<Row> spare = new ArrayList<>(registered.stream()
                    .filter(row -> row.bucket() != Bucket.CAMPFIRE && !chosen.contains(row.id())).toList());
            Collections.shuffle(spare, random);
            result.addAll(spare.stream().limit(WanderingPool.GOODS_PER_VISIT - result.size()).toList());
        }
        registered.stream().filter(row -> row.bucket() == Bucket.CAMPFIRE).findFirst().ifPresent(result::add);
        return List.copyOf(result);
    }
}

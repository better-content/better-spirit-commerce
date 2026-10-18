package com.bettercontent.spiritcommerce.trader;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/** Finite authored stock; replenishment is explicit and never triggered by purchase. */
public final class AuthoredStockLedger {
    private final Map<String, Integer> remaining = new HashMap<>();

    public void seed(final String offer, final int units) {
        if (offer == null || offer.isBlank() || units < 0) throw new IllegalArgumentException("invalid authored stock");
        remaining.putIfAbsent(offer, units);
    }

    public boolean purchase(final String offer, final int units) {
        if (units <= 0 || remaining.getOrDefault(offer, 0) < units) return false;
        remaining.put(offer, remaining.get(offer) - units);
        return true;
    }

    /** Explicit authoring or restock command only. */
    public void replenish(final String offer, final int units) {
        if (offer == null || offer.isBlank() || units <= 0) throw new IllegalArgumentException("invalid replenishment");
        remaining.merge(offer, units, Math::addExact);
    }

    public int remaining(final String offer) { return remaining.getOrDefault(offer, 0); }
    public boolean contains(final String offer) { return remaining.containsKey(offer); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        remaining.forEach((offer, units) -> tag.putInt(offer, units));
        return tag;
    }

    public static AuthoredStockLedger load(final CompoundTag tag) {
        AuthoredStockLedger ledger = new AuthoredStockLedger();
        for (String offer : tag.getAllKeys()) ledger.remaining.put(offer, Math.max(0, tag.getInt(offer)));
        return ledger;
    }

    /** Coalesces the former payment/price/result keys into one physical output-item balance. */
    static AuthoredStockLedger migrateOfferKeys(final CompoundTag tag) {
        AuthoredStockLedger ledger = new AuthoredStockLedger();
        for (String oldKey : tag.getAllKeys()) {
            int output = oldKey.lastIndexOf("=>");
            int count = oldKey.lastIndexOf('#');
            if (output < 0 || count <= output + 2) continue;
            String commodity = oldKey.substring(output + 2, count);
            int units = Math.max(0, tag.getInt(oldKey));
            if (!commodity.isBlank()) {
                ledger.remaining.merge(commodity, units, Math::addExact);
            }
        }
        return ledger;
    }
}

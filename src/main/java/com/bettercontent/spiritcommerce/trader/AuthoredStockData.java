package com.bettercontent.spiritcommerce.trader;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.saveddata.SavedData;

/** Shared finite import stock. New commodity keys are seeded once; existing balances never refill. */
public final class AuthoredStockData extends SavedData {
    private static final String DATA_NAME = "better_spirit_commerce_authored_stock";
    private static final String STOCK_TAG = "stock";
    private static final String VERSION_TAG = "version";
    private static final int VERSION = 3;
    private final AuthoredStockLedger ledger;

    private AuthoredStockData() {
        ledger = new AuthoredStockLedger();
        AuthoredStockPolicy.initialStockUnits().forEach(ledger::seed);
        setDirty();
    }

    private AuthoredStockData(final AuthoredStockLedger ledger) { this.ledger = ledger; }

    public static AuthoredStockData get(final ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                AuthoredStockData::load, AuthoredStockData::new, DATA_NAME);
    }

    public static AuthoredStockData load(final CompoundTag tag) {
        int version = tag.getInt(VERSION_TAG);
        if (version < 1 || version > VERSION) {
            throw new IllegalStateException("Unsupported authored merchant stock data version " + version);
        }
        AuthoredStockLedger ledger = version == 1
                ? AuthoredStockLedger.migrateOfferKeys(tag.getCompound(STOCK_TAG))
                : AuthoredStockLedger.load(tag.getCompound(STOCK_TAG));
        boolean changed = version != VERSION;
        for (var stock : AuthoredStockPolicy.initialStockUnits().entrySet()) {
            if (!ledger.contains(stock.getKey())) {
                // Only the v1/v2 migration grants newly introduced spirit-import stock.
                // A missing known key in v3 (or any missing legacy-global key) fails closed;
                // it is not an invitation to grant another allocation on reload.
                boolean newImport = version < VERSION
                        && !AuthoredStockPolicy.legacyGlobalCommodity(stock.getKey());
                ledger.seed(stock.getKey(), newImport ? stock.getValue() : 0);
                changed = true;
            }
        }
        AuthoredStockData data = new AuthoredStockData(ledger);
        if (changed) data.setDirty();
        return data;
    }

    static AuthoredStockData createForTest(final Map<String, Integer> stock) {
        AuthoredStockLedger ledger = new AuthoredStockLedger();
        stock.forEach(ledger::seed);
        return new AuthoredStockData(ledger);
    }

    public boolean canPurchase(final MerchantOffer offer) {
        if (!AuthoredStockPolicy.isFinite(offer)) return true;
        return canPurchase(AuthoredOfferStock.key(offer), offer.getResult().getCount());
    }

    public boolean recordPurchase(final MerchantOffer offer) {
        if (!AuthoredStockPolicy.isFinite(offer)) return true;
        return recordPurchase(AuthoredOfferStock.key(offer), offer.getResult().getCount());
    }

    boolean canPurchase(final String item, final int outputUnits) {
        if (!ledger.contains(item)) return !AuthoredStockPolicy.initialStockUnits().containsKey(item);
        return outputUnits > 0 && ledger.remaining(item) >= outputUnits;
    }

    boolean recordPurchase(final String item, final int outputUnits) {
        if (!ledger.contains(item)) return !AuthoredStockPolicy.initialStockUnits().containsKey(item);
        boolean purchased = outputUnits > 0 && ledger.purchase(item, outputUnits);
        if (purchased) setDirty();
        return purchased;
    }

    int remaining(final String item) { return ledger.remaining(item); }

    int remainingUnits(final MerchantOffer offer) {
        if (!AuthoredStockPolicy.isFinite(offer)) return Integer.MAX_VALUE;
        String key = AuthoredOfferStock.key(offer);
        return ledger.contains(key) ? ledger.remaining(key) : 0;
    }

    @Override public CompoundTag save(final CompoundTag tag) {
        tag.putInt(VERSION_TAG, VERSION);
        tag.put(STOCK_TAG, ledger.save());
        return tag;
    }
}

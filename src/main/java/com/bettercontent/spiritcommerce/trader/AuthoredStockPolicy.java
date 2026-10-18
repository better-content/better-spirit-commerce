package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.config.WanderingPool;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.registries.ForgeRegistries;

/** Explicit commodity ceilings, never derived from a sampled menu or the number of sellers. */
public final class AuthoredStockPolicy {
    private static final Map<String, Integer> INITIAL_STOCK_UNITS = stockUnits();
    private static final Set<ResourceLocation> ALL_MERCHANTS = WanderingPool.commodityStocks().stream()
            .filter(stock -> stock.scope() == WanderingPool.StockScope.ALL_MERCHANTS)
            .map(stock -> new ResourceLocation(stock.item())).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private AuthoredStockPolicy() {}

    public static boolean isFinite(final MerchantOffer offer) {
        if (offer == null) return false;
        ResourceLocation result = ForgeRegistries.ITEMS.getKey(offer.getResult().getItem());
        return isFinite(result, spiritPayment(offer.getBaseCostA()) || spiritPayment(offer.getCostB()));
    }

    private static boolean spiritPayment(final ItemStack stack) {
        return !stack.isEmpty() && CurrencyIdentity.fromItemId(ForgeRegistries.ITEMS.getKey(stack.getItem())) != null;
    }

    static boolean isFinite(final ResourceLocation result, final boolean spiritPayment) {
        return isFinite(result) && (ALL_MERCHANTS.contains(result) || spiritPayment);
    }

    static boolean isFinite(final ResourceLocation result) {
        return result != null && INITIAL_STOCK_UNITS.containsKey(result.toString());
    }

    static boolean legacyGlobalCommodity(final String item) {
        return ALL_MERCHANTS.contains(new ResourceLocation(item));
    }

    static Map<String, Integer> initialStockUnits() { return INITIAL_STOCK_UNITS; }

    private static Map<String, Integer> stockUnits() {
        Map<String, Integer> stock = new HashMap<>();
        for (var row : WanderingPool.commodityStocks()) stock.put(row.item(), row.units());
        return Map.copyOf(stock);
    }

    /** Small aggregation helper for callers/tests; not the source of the live seed ceilings. */
    static Map<String, Integer> aggregate(final List<StockOffer> offers) {
        Map<String, Integer> stock = new HashMap<>();
        for (StockOffer offer : offers) {
            if (offer != null && isFinite(offer.result()) && offer.count() > 0 && offer.maxUses() > 0) {
                stock.merge(offer.result().toString(), Math.multiplyExact(offer.count(), offer.maxUses()), Math::addExact);
            }
        }
        return Map.copyOf(stock);
    }

    record StockOffer(ResourceLocation result, int count, int maxUses) {}
}

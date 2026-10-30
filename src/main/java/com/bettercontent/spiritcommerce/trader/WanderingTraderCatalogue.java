package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy;

import com.bettercontent.spiritcommerce.config.EconomyPolicy;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.betterdimensionfonts.trade.DimensionalFontMapTrades;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Set;

/** Eight fixed wandering markets. Each visit exposes thirteen goods plus its profession egg. */
public final class WanderingTraderCatalogue {
    private WanderingTraderCatalogue() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(final WandererTradesEvent event) {
        event.getGenericTrades().clear();
        event.getRareTrades().clear();
        for (int index = 0; index < 5; index++) event.getGenericTrades().add(new BootstrapListing());
        event.getRareTrades().add(new BootstrapListing());
    }

    public static void ensureThemedOffers(final WanderingTrader trader, final MerchantOffers offers) {
        WanderingTraderTheme theme = WanderingTraderTheme.fromId(trader.getPersistentData().getString(WanderingTraderVisits.THEME_TAG));
        if (theme == null) theme = WanderingTraderTheme.forUuid(trader.getUUID());
        SpiritKind spirit = SpiritKind.fromId(theme.id());
        Item payment = CurrencyItems.item(theme.currencyIdentity()).get();
        offers.removeIf(offer -> !RenewableAcquisitionPolicy.allowsMerchant(trader,offer.getResult()));
        boolean cataloguePresent = hasAuthoredCatalogue(offers, spirit, payment);
        if (!cataloguePresent) {
            offers.clear();
            appendBaseOffers(offers, spirit, payment);
        }
        appendFontOfferIfAvailable(trader, offers, payment, spirit);
    }

    private static boolean hasAuthoredCatalogue(final MerchantOffers offers, final SpiritKind spirit, final Item payment) {
        var authoredRows = EconomyPolicy.wanderingRows(spirit);
        boolean[] rowsFound = new boolean[authoredRows.size()];
        // Intentionally filtered rows are not missing stock: never rebuild/reset permitted offers.
        for (int index = 0; index < authoredRows.size(); index++) {
            var row = authoredRows.get(index); Item output = item(row.result().id());
            rowsFound[index] = output != null && !RenewableAcquisitionPolicy.allows(new ItemStack(output,row.result().count()));
        }
        boolean eggFound = false;
        boolean fontFound = false;
        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();
            if (result.is(net.minecraft.world.item.Items.VILLAGER_SPAWN_EGG)) {
                if (eggFound || !offer.getBaseCostA().is(payment) || offer.getBaseCostA().getCount() != VillageStarterOffer.SPIRIT_COST
                        || result.getCount() != VillageStarterOffer.VILLAGER_COUNT) return false;
                eggFound = true;
                continue;
            }
            if (isFontMapOffer(offer)) {
                if (fontFound || !offer.getBaseCostA().is(payment)) return false;
                fontFound = true;
                continue;
            }
            boolean matched = false;
            var rows = EconomyPolicy.wanderingRows(spirit);
            for (int index = 0; index < rows.size(); index++) {
                EconomyPolicy.WanderingRow row = rows.get(index);
                ResourceLocation outputId = new ResourceLocation(row.result().id());
                if (!rowsFound[index] && outputId.equals(ForgeRegistries.ITEMS.getKey(result.getItem()))
                        && result.getCount() == row.result().count()
                        && offer.getBaseCostA().is(payment) && offer.getBaseCostA().getCount() == row.cost()) {
                    rowsFound[index] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        if (!eggFound) return false;
        for (boolean found : rowsFound) if (!found) return false;
        return true;
    }

    private static void appendBaseOffers(final MerchantOffers offers, final SpiritKind spirit, final Item payment) {
        for (EconomyPolicy.WanderingRow row : EconomyPolicy.wanderingRows(spirit)) {
            Item result = item(row.result().id());
            if (payment != null && result != null
                    && RenewableAcquisitionPolicy.allows(new ItemStack(result,row.result().count()))) offers.add(new MerchantOffer(
                    new ItemStack(payment, row.cost()), new ItemStack(result, row.result().count()),
                    row.maxUses(), row.xp(), 0.0F));
        }
    }

    /** Adds one surveyed Font map without rebuilding or resetting an existing catalogue. */
    static boolean appendFontOfferIfAvailable(final WanderingTrader trader, final MerchantOffers offers,
                                              final Item payment, final SpiritKind spirit) {
        if (payment == null || !(trader.level() instanceof ServerLevel level)) return false;
        Set<String> allowed = FontTradeAffinity.destinations(spirit);
        DimensionalFontMapTrades.setSellerDefinitionIds(trader.getPersistentData(), allowed);
        if (offers.stream().anyMatch(WanderingTraderCatalogue::isFontMapOffer)) return false;
        Set<String> soldTypes = DimensionalFontMapTrades.soldDefinitionIds(trader.getPersistentData());
        MerchantOffer mapOffer = DimensionalFontMapTrades.authoredSellerOffer(
                level, trader.blockPosition(), 6, payment, soldTypes, allowed);
        return appendFontOffer(offers, mapOffer);
    }

    static boolean appendFontOffer(final MerchantOffers offers, final MerchantOffer mapOffer) {
        if (mapOffer == null || isFontMapOffer(mapOffer)
                && offers.stream().anyMatch(WanderingTraderCatalogue::isFontMapOffer)) return false;
        offers.add(mapOffer);
        return true;
    }

    private static boolean isFontMapOffer(final MerchantOffer offer) {
        return offer.getResult().getTag() != null
                && offer.getResult().getTag().contains("better_dimension_fonts:font_definition_id");
    }

    public static int themeCount() { return WanderingTraderTheme.values().length; }
    public static int authoredRowCount() { return EconomyPolicy.wanderingRowCount(); }

    private static Item item(final String id) { return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)); }
    private record BootstrapListing() implements VillagerTrades.ItemListing {
        @Override public MerchantOffer getOffer(final Entity entity, final RandomSource random) {
            WanderingTraderTheme theme = entity instanceof WanderingTrader trader
                    ? WanderingTraderTheme.fromId(trader.getPersistentData().getString(WanderingTraderVisits.THEME_TAG)) : null;
            if (theme == null && entity != null) theme = WanderingTraderTheme.forUuid(entity.getUUID());
            if (theme == null) theme = WanderingTraderTheme.SACRED;
            Item payment = CurrencyItems.item(theme.currencyIdentity()).get();
            return payment == null ? null : new MerchantOffer(new ItemStack(payment),
                    new ItemStack(net.minecraft.world.item.Items.BREAD), 1, 0, 0.0F);
        }
    }
}

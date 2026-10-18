package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.config.WanderingPool;
import com.bettercontent.spiritcommerce.config.WanderingPool.Row;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.spiritcommerce.registry.SpiritProfessions;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.bettercontent.betterdimensionfonts.trade.DimensionalFontMapTrades;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Twelve sampled themed goods plus the matching fire; native eggs and Font maps stay separate. */
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
        WanderingTraderTheme theme = WanderingTraderTheme.fromId(
                trader.getPersistentData().getString(WanderingTraderVisits.THEME_TAG));
        if (theme == null) theme = WanderingTraderTheme.forUuid(trader.getUUID());
        SpiritKind spirit = theme.spirit();
        Item payment = CurrencyItems.item(theme.currencyIdentity()).get();
        List<Row> selected = selectedRows(trader, spirit);
        if (!hasSelectedCatalogue(offers, selected, payment)) {
            rebuildPreservingUses(offers, selected, payment, spirit);
        }
        appendFontOfferIfAvailable(trader, offers, payment, spirit);
    }

    /** UUID and theme are persisted by vanilla/the visit owner; no RNG is consumed on reopening. */
    static List<Row> selectedRows(final WanderingTrader trader, final SpiritKind kind) {
        return WanderingOfferSelector.select(WanderingPool.rows(kind), trader.getUUID(), row -> {
            ResourceLocation id = new ResourceLocation(row.result().id());
            return ForgeRegistries.ITEMS.containsKey(id) && ForgeRegistries.ITEMS.getValue(id) != Items.AIR;
        });
    }

    private static boolean hasSelectedCatalogue(final MerchantOffers offers, final List<Row> selected,
                                                 final Item payment) {
        Map<ResourceLocation, Row> remaining = new HashMap<>();
        for (Row row : selected) remaining.put(new ResourceLocation(row.result().id()), row);
        boolean egg = false, map = false;
        for (MerchantOffer offer : offers) {
            if (offer.getResult().is(Items.VILLAGER_SPAWN_EGG)) {
                if (egg || !offer.getBaseCostA().is(payment)) return false;
                egg = true;
            } else if (isFontMapOffer(offer)) {
                if (map || !offer.getBaseCostA().is(payment)) return false;
                map = true;
            } else {
                Row row = remaining.remove(ForgeRegistries.ITEMS.getKey(offer.getResult().getItem()));
                if (row == null || !offer.getBaseCostA().is(payment)
                        || offer.getBaseCostA().getCount() != row.cost() || !offer.getCostB().isEmpty()
                        || offer.getResult().getCount() != row.result().count()
                        || offer.getMaxUses() != row.maxUses() || !validNativeState(offer.getResult())) return false;
            }
        }
        return remaining.isEmpty();
    }

    private static boolean validNativeState(final ItemStack stack) {
        return !stack.is(Items.FIREWORK_ROCKET)
                || stack.getTag() != null && stack.getTag().getCompound("Fireworks").getByte("Flight") == 1
                && !stack.getTag().getCompound("Fireworks").contains("Explosions");
    }

    /** Also carries already-spent units when migrating old menus or repairing foreign listings. */
    private static void rebuildPreservingUses(final MerchantOffers offers, final List<Row> selected,
                                              final Item payment, final SpiritKind spirit) {
        Map<ResourceLocation, Long> spentUnits = new HashMap<>();
        List<MerchantOffer> special = new ArrayList<>();
        String profession = SpiritProfessions.definition(spirit).profession().getId().toString();
        Set<ResourceLocation> specialItems = new HashSet<>();
        for (MerchantOffer old : offers) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(old.getResult().getItem());
            if (old.getResult().is(Items.VILLAGER_SPAWN_EGG)) {
                var tag = old.getResult().getTag();
                if (old.getBaseCostA().is(payment) && tag != null && profession.equals(
                        tag.getCompound("EntityTag").getCompound("VillagerData").getString("profession"))
                        && specialItems.add(id)) special.add(old);
            } else if (isFontMapOffer(old)) {
                if (old.getBaseCostA().is(payment) && special.stream().noneMatch(WanderingTraderCatalogue::isFontMapOffer)) {
                    special.add(old);
                }
            } else if (id != null) {
                spentUnits.merge(id, (long) Math.max(0, old.getUses()) * old.getResult().getCount(), Long::sum);
            }
        }
        offers.clear();
        for (Row row : selected) {
            Item result = ForgeRegistries.ITEMS.getValue(new ResourceLocation(row.result().id()));
            if (result == null || result == Items.AIR) continue;
            ItemStack output = result.getDefaultInstance();
            output.setCount(row.result().count());
            if (output.is(Items.FIREWORK_ROCKET)) {
                output.getOrCreateTagElement("Fireworks").putByte("Flight", (byte) 1);
                output.getOrCreateTagElement("Fireworks").remove("Explosions");
            }
            long spent = spentUnits.getOrDefault(ForgeRegistries.ITEMS.getKey(result), 0L);
            int uses = (int) Math.min(row.maxUses(), (spent + row.result().count() - 1) / row.result().count());
            MerchantOffer offer = new MerchantOffer(new ItemStack(payment, row.cost()), ItemStack.EMPTY,
                    output, uses, row.maxUses(), row.xp(), 0.0F);
            offers.add(offer);
        }
        offers.addAll(special);
    }

    /** Adds a genuine surveyed Font map without rebuilding/resetting selected goods. */
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
    public static int authoredRowCount() { return WanderingPool.rowCount(); }

    private record BootstrapListing() implements VillagerTrades.ItemListing {
        @Override public MerchantOffer getOffer(final Entity entity, final net.minecraft.util.RandomSource random) {
            WanderingTraderTheme theme = entity instanceof WanderingTrader trader
                    ? WanderingTraderTheme.fromId(trader.getPersistentData().getString(WanderingTraderVisits.THEME_TAG)) : null;
            if (theme == null && entity != null) theme = WanderingTraderTheme.forUuid(entity.getUUID());
            if (theme == null) theme = WanderingTraderTheme.SACRED;
            Item payment = CurrencyItems.item(theme.currencyIdentity()).get();
            return payment == null ? null : new MerchantOffer(new ItemStack(payment),
                    new ItemStack(Items.BREAD), 1, 0, 0.0F);
        }
    }
}

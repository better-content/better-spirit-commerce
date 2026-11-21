package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy;
import com.bettercontent.spiritcommerce.config.EconomyPolicy;
import com.bettercontent.spiritcommerce.registry.SpiritProfessions;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.bettercontent.betterdimensionfonts.trade.DimensionalFontMapTrades;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored spirit offers for the five specialist villager professions. */
public final class VillagerCatalogue {
    private VillagerCatalogue() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(final VillagerTradesEvent event) {
        SpiritKind kind = SpiritProfessions.kindOf(event.getType());
        if (!replaceListings(kind, event.getTrades())) return;
        // Vanilla rolls only two listings per level and does not retry a null
        // offer. Do not let absent optional items or denied NPC outputs consume
        // both draws and leave a specialist unable to trade.
        event.getTrades().values().forEach(listings ->
                listings.removeIf(listing -> listing instanceof SpiritListing authored && !authored.available()));
    }

    static boolean replaceListings(
            final SpiritKind kind,
            final Map<Integer, List<VillagerTrades.ItemListing>> trades) {
        if (kind == null) return false;
        trades.clear();
        for (EconomyPolicy.VillagerRow row : EconomyPolicy.villagerRows(kind)) {
            trades.computeIfAbsent(row.level(), ignored -> new ArrayList<>())
                    .add(new SpiritListing(kind, row));
        }
        trades.computeIfAbsent(2, ignored -> new ArrayList<>()).add(new FontListing(kind));
        return true;
    }

    public static int rowCount() { return EconomyPolicy.villagerRowCount(); }
    public static boolean allRowsUseExactlyOneSpiritSide() { return true; }

    /** Makes surveyed maps available to villagers leveled before the first site was indexed. */
    public static void ensureFontOfferIfAvailable(final Villager villager, final MerchantOffers offers) {
        if (villager.getVillagerData().getLevel() < 2 || !(villager.level() instanceof ServerLevel level)) return;
        SpiritKind kind = SpiritProfessions.kindOf(villager.getVillagerData().getProfession());
        if (kind == null) return;
        var allowed = FontTradeAffinity.destinations(kind);
        DimensionalFontMapTrades.setSellerDefinitionIds(villager.getPersistentData(), allowed);
        if (offers.stream().anyMatch(offer -> offer.getResult().getTag() != null
                && offer.getResult().getTag().contains("better_dimension_fonts:font_definition_id"))) return;
        Item spirit = CurrencyItems.item(kind.currencyIdentity()).get();
        MerchantOffer map = DimensionalFontMapTrades.authoredSellerOffer(level, villager.blockPosition(),
                5, spirit, DimensionalFontMapTrades.soldDefinitionIds(villager.getPersistentData()), allowed);
        if (map != null) offers.add(map);
    }
    private record SpiritListing(SpiritKind kind, EconomyPolicy.VillagerRow row) implements VillagerTrades.ItemListing {
        private boolean available() {
            Item result = item(row.result().id());
            return CurrencyItems.item(kind.currencyIdentity()).get() != Items.AIR && result != Items.AIR
                    && RenewableAcquisitionPolicy.allows(new ItemStack(result, row.result().count()));
        }

        @Override public MerchantOffer getOffer(final Entity entity, final RandomSource random) {
            if (!available()) return null;
            return new MerchantOffer(new ItemStack(CurrencyItems.item(kind.currencyIdentity()).get(), row.cost()),
                    new ItemStack(item(row.result().id()), row.result().count()), row.maxUses(), row.xp(), 0.0F);
        }
    }

    private record FontListing(SpiritKind kind) implements VillagerTrades.ItemListing {
        @Override public MerchantOffer getOffer(final Entity entity, final RandomSource random) {
            if (!(entity instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return null;
            Item spirit = CurrencyItems.item(kind.currencyIdentity()).get();
            var allowed = FontTradeAffinity.destinations(kind);
            DimensionalFontMapTrades.setSellerDefinitionIds(villager.getPersistentData(), allowed);
            return DimensionalFontMapTrades.authoredSellerOffer(level, villager.blockPosition(),
                    5, spirit, DimensionalFontMapTrades.soldDefinitionIds(villager.getPersistentData()), allowed);
        }
    }

    private static Item item(final String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null ? Items.AIR : item;
    }
}

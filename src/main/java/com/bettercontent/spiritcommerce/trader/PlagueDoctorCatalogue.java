package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy;

import com.bettercontent.spiritcommerce.config.EconomyPolicy;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.registries.ForgeRegistries;

/** A daily cabinet of eight strange, mixed-spirit specimens. */
public final class PlagueDoctorCatalogue {
    static final ResourceLocation PLAGUE_DOCTOR = new ResourceLocation("rats", "plague_doctor");
    static final String VERSION_TAG = "better_spirit_commerce.plague_catalogue_version";
    static final String DAY_TAG = "better_spirit_commerce.plague_catalogue_day";
    static final String DAILY_OFFER_COUNT_TAG = "better_spirit_commerce.plague_catalogue_offer_count";
    static final int VERSION = 1;
    private static final int DAILY_OFFERS = 8;

    private PlagueDoctorCatalogue() {}

    public static boolean isPlagueDoctor(final Entity entity) {
        return PLAGUE_DOCTOR.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }

    /** True only for a current, deterministic offer row in this doctor's daily authored catalogue. */
    static boolean isAuthoredDailyOffer(final AbstractVillager doctor, final MerchantOffer offer) {
        if (doctor == null || offer == null || !isPlagueDoctor(doctor)
                || !(doctor.level() instanceof ServerLevel level)) return false;
        long day = Math.floorDiv(level.getServer().overworld().getDayTime(), 24_000L);
        var data = doctor.getPersistentData();
        if (data.getInt(VERSION_TAG) != VERSION || data.getLong(DAY_TAG) != day) return false;
        MerchantOffers offers = doctor.getOffers();
        int offerIndex = -1;
        for (int index = 0; index < offers.size(); index++) {
            if (offers.get(index) == offer) {
                offerIndex = index;
                break;
            }
        }
        List<EconomyPolicy.PlagueDoctorRow> selected = selectRows(doctor.getUUID(), day,
                PlagueDoctorCatalogue::isAvailableItem);
        return isAuthoredDailyOffer(offerIndex, data.getInt(DAILY_OFFER_COUNT_TAG),
                data.getInt(VERSION_TAG), day, data.getLong(DAY_TAG), selected, signature(offer));
    }

    static boolean isAuthoredDailyOffer(final int offerIndex, final int authoredOfferCount,
                                         final int catalogueVersion, final long currentDay, final long catalogueDay,
                                         final List<EconomyPolicy.PlagueDoctorRow> selected,
                                         final DailyOfferSignature offer) {
        return catalogueVersion == VERSION && catalogueDay == currentDay
                && offerIndex >= 0 && offerIndex < authoredOfferCount && offerIndex < selected.size()
                && offer != null && signature(selected.get(offerIndex)).equals(offer);
    }

    private static boolean isAvailableItem(final ResourceLocation id) {
        return item(id) != Items.AIR;
    }

    static DailyOfferSignature signature(final EconomyPolicy.PlagueDoctorRow row) {
        return new DailyOfferSignature(row.kind().currencyIdentity().itemId(), row.cost(), null, 0,
                new ResourceLocation(row.result().id()), row.result().count(), row.maxUses(), row.xp());
    }

    private static DailyOfferSignature signature(final MerchantOffer offer) {
        return new DailyOfferSignature(itemId(offer.getBaseCostA()), offer.getBaseCostA().getCount(),
                itemId(offer.getCostB()), offer.getCostB().getCount(),
                itemId(offer.getResult()), offer.getResult().getCount(), offer.getMaxUses(), offer.getXp());
    }

    private static ResourceLocation itemId(final ItemStack stack) {
        return stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
    }

    record DailyOfferSignature(ResourceLocation payment, int paymentCount,
                               ResourceLocation secondPayment, int secondPaymentCount,
                               ResourceLocation result, int resultCount, int maxUses, int xp) {}

    public static void ensureOffers(final AbstractVillager doctor, final MerchantOffers offers) {
        if (!(doctor.level() instanceof ServerLevel level)) return;
        long day = Math.floorDiv(level.getServer().overworld().getDayTime(), 24_000L);
        ensureOffers(doctor, offers, day);
    }

    static void ensureOffers(final AbstractVillager doctor, final MerchantOffers offers, final long day) {
        offers.removeIf(offer -> !RenewableAcquisitionPolicy.allowsMerchant(doctor,offer.getResult()));
        var data = doctor.getPersistentData();
        boolean current = data.getInt(VERSION_TAG) == VERSION && data.getLong(DAY_TAG) == day;
        if (current && !offers.isEmpty()) {
            if (!data.contains(DAILY_OFFER_COUNT_TAG)) {
                data.putInt(DAILY_OFFER_COUNT_TAG, authoredPrefixCount(doctor.getUUID(), day, offers));
            }
            return;
        }
        if (doctor.getTradingPlayer() != null) return;

        List<EconomyPolicy.PlagueDoctorRow> selected = selectRows(doctor.getUUID(), day,
                PlagueDoctorCatalogue::isAvailableItem);
        offers.clear();
        for (EconomyPolicy.PlagueDoctorRow row : selected) {
            Item payment = CurrencyItems.item(row.kind().currencyIdentity()).get();
            Item result = item(new ResourceLocation(row.result().id()));
            if (payment == Items.AIR || result == Items.AIR
                    || !RenewableAcquisitionPolicy.allows(new ItemStack(result,row.result().count()))) continue;
            offers.add(new MerchantOffer(new ItemStack(payment, row.cost()),
                    new ItemStack(result, row.result().count()), row.maxUses(), row.xp(), 0.0F));
        }
        data.putInt(VERSION_TAG, VERSION);
        data.putLong(DAY_TAG, day);
        data.putInt(DAILY_OFFER_COUNT_TAG, offers.size());
    }

    private static int authoredPrefixCount(final UUID doctorId, final long day, final MerchantOffers offers) {
        List<EconomyPolicy.PlagueDoctorRow> selected = selectRows(doctorId, day,
                PlagueDoctorCatalogue::isAvailableItem).stream()
                .filter(row -> RenewableAcquisitionPolicy.allows(new ItemStack(item(new ResourceLocation(row.result().id())),row.result().count())))
                .toList();
        int count = 0;
        while (count < selected.size() && count < offers.size()
                && signature(selected.get(count)).equals(signature(offers.get(count)))) count++;
        return count;
    }

    static List<EconomyPolicy.PlagueDoctorRow> selectRows(final UUID doctorId, final long day,
                                                           final Predicate<ResourceLocation> available) {
        List<EconomyPolicy.PlagueDoctorRow> candidates = new ArrayList<>();
        for (EconomyPolicy.PlagueDoctorRow row : EconomyPolicy.plagueDoctorRows()) {
            ResourceLocation result = new ResourceLocation(row.result().id());
            CurrencyIdentity currency = row.kind().currencyIdentity();
            if (currency != null && available.test(currency.itemId()) && available.test(result)) candidates.add(row);
        }
        long seed = doctorId.getMostSignificantBits()
                ^ Long.rotateLeft(doctorId.getLeastSignificantBits(), 17)
                ^ day * 0x9E3779B97F4A7C15L;
        RandomSource random = RandomSource.create(seed);
        for (int index = candidates.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            EconomyPolicy.PlagueDoctorRow held = candidates.get(index);
            candidates.set(index, candidates.get(swap));
            candidates.set(swap, held);
        }
        return List.copyOf(candidates.subList(0, Math.min(DAILY_OFFERS, candidates.size())));
    }

    private static Item item(final ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? Items.AIR : item;
    }
}

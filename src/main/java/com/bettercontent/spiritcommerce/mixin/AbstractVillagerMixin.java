package com.bettercontent.spiritcommerce.mixin;

import com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy;
import com.bettercontent.spiritcommerce.trader.MerchantCurrencyPolicy;
import com.bettercontent.spiritcommerce.trader.PlagueDoctorCatalogue;
import com.bettercontent.spiritcommerce.trader.VillageStarterOffer;
import com.bettercontent.spiritcommerce.trader.WanderingTraderVisits;
import com.bettercontent.spiritcommerce.trader.WanderingTraderCatalogue;
import com.bettercontent.spiritcommerce.trader.VillagerCatalogue;
import com.bettercontent.spiritcommerce.resident.ResidentRules;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractVillager.class)
abstract class AbstractVillagerMixin {
    @Inject(method = "getOffers", at = @At("RETURN"))
    private void betterContentEconomy$applyTradePolicy(final CallbackInfoReturnable<MerchantOffers> callback) {
        AbstractVillager merchant = (AbstractVillager) (Object) this;
        MerchantOffers offers = callback.getReturnValue();
        if (merchant instanceof Villager && ResidentRules.isResident(merchant)) {
            offers.clear();
            return;
        }
        MerchantCurrencyPolicy.normalize(offers);
        if (PlagueDoctorCatalogue.isPlagueDoctor(merchant)) {
            PlagueDoctorCatalogue.ensureOffers(merchant, offers);
        }
        if (merchant instanceof WanderingTrader trader
                && trader.getPersistentData().contains(WanderingTraderVisits.THEME_TAG)) {
            WanderingTraderCatalogue.ensureThemedOffers(trader, offers);
            VillageStarterOffer.ensurePresent(trader, offers);
        }
        if (merchant instanceof Villager villager) {
            VillagerCatalogue.ensureFontOfferIfAvailable(villager, offers);
        }
        // Final server-side NPC filter AFTER custom daily/themed/font/third-party appends.
        // Resident finite-stock barter does not use MerchantOffers and is deliberately untouched.
        offers.removeIf(offer -> !RenewableAcquisitionPolicy.allowsMerchant(merchant,offer.getResult()));
    }
}

package com.bettercontent.spiritcommerce.mixin;

import com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Never install a newly denied NPC preview output; payment slots remain untouched. */
@Mixin(MerchantContainer.class)
abstract class NpcMerchantContainerMixin {
    @Shadow @Final private Merchant merchant;
    @Shadow @Final private NonNullList<ItemStack> itemStacks;
    @Shadow private MerchantOffer activeOffer;
    @Shadow private int futureXp;
    @Inject(method = "updateSellItem", at = @At("RETURN"))
    private void betterCommerce$filterNpcPreview(CallbackInfo ci) {
        if (activeOffer != null && !RenewableAcquisitionPolicy.allowsMerchant(merchant,activeOffer.getResult())) {
            itemStacks.set(2,ItemStack.EMPTY); activeOffer = null; futureXp = 0;
        }
    }
}

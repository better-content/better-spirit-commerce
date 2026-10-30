package com.bettercontent.spiritcommerce.mixin;

import com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shift-click moves the result BEFORE onTake, so a stale NPC offer must be vetoed here too. */
@Mixin(MerchantMenu.class)
abstract class NpcMerchantMenuMixin {
    @Shadow @Final private Merchant trader;
    @Shadow @Final private MerchantContainer tradeContainer;
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void betterCommerce$beforeShiftClickOutput(Player player, int index, CallbackInfoReturnable<ItemStack> ci) {
        if (index != 2) return;
        var offer = tradeContainer.getActiveOffer();
        ItemStack output = offer == null ? tradeContainer.getItem(2) : offer.getResult();
        if (!RenewableAcquisitionPolicy.allowsMerchant(trader,output)) ci.setReturnValue(ItemStack.EMPTY);
    }
}

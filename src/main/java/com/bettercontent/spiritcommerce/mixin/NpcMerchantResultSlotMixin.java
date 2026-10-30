package com.bettercontent.spiritcommerce.mixin;

import com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Owner-aware only. Never mixes into ownerless MerchantOffer.take or blocks player-funded merchants. */
@Mixin(MerchantResultSlot.class)
abstract class NpcMerchantResultSlotMixin extends Slot {
    @Shadow @Final private Merchant merchant;
    @Shadow @Final private MerchantContainer slots;
    private NpcMerchantResultSlotMixin(Container container, int slot, int x, int y) { super(container,slot,x,y); }
    private boolean betterCommerce$allowed() {
        var offer = slots.getActiveOffer();
        return RenewableAcquisitionPolicy.allowsMerchant(merchant,offer == null ? getItem() : offer.getResult());
    }
    @Override public boolean mayPickup(Player player) { return betterCommerce$allowed() && super.mayPickup(player); }
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void betterCommerce$beforeOutputRemoval(int amount, CallbackInfoReturnable<ItemStack> ci) {
        if (!betterCommerce$allowed()) ci.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void betterCommerce$beforeAchievementsAndPayment(Player player, ItemStack result, CallbackInfo ci) {
        if (!betterCommerce$allowed() || !RenewableAcquisitionPolicy.allowsMerchant(merchant,result)) ci.cancel();
    }
    @Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffer;take(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"), require = 2)
    private boolean betterCommerce$ownerAwarePayment(MerchantOffer offer, ItemStack first, ItemStack second) {
        return RenewableAcquisitionPolicy.allowsMerchant(merchant,offer.getResult()) && offer.take(first,second);
    }
}

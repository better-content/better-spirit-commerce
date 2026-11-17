package com.bettercontent.spiritcommerce.mixin;

import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.sammy.malum.common.entity.FloatingItemEntity;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FloatingItemEntity.class, remap = false)
abstract class FloatingItemDefaultMixin {
    @Inject(method = "getDefaultItem", at = @At("HEAD"), cancellable = true)
    private void betterContentEconomy$defaultSpirit(CallbackInfoReturnable<Item> callback) {
        callback.setReturnValue(CurrencyItems.item(CurrencyIdentity.POWER).get());
    }
}

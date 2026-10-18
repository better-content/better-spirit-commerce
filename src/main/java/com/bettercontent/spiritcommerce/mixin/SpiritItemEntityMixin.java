package com.bettercontent.spiritcommerce.mixin;

import com.bettercontent.spiritcommerce.api.event.SpiritAcquiredEvent;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.bettercontent.spiritcommerce.spirit.SpiritAcquisition;
import com.sammy.malum.common.entity.spirit.SpiritItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpiritItemEntity.class, remap = false)
abstract class SpiritItemEntityMixin {
    @Unique private ItemStack betterContentEconomy$collectedStack = ItemStack.EMPTY;
    @Inject(method = "collect", at = @At("HEAD"), remap = false)
    private void betterContentEconomy$cleanGroupedPickup(final CallbackInfo callback) {
        ItemStack stack = ((SpiritItemEntity) (Object) this).getItem();
        SpiritAcquisition.removeGroupedPickupMarker(stack);
        betterContentEconomy$collectedStack = stack.copy();
    }

    @Inject(method = "collect", at = @At("TAIL"), remap = false)
    private void betterContentEconomy$signalCollection(final CallbackInfo callback) {
        SpiritItemEntity self = (SpiritItemEntity) (Object) this;
        ItemStack stack = betterContentEconomy$collectedStack;
        betterContentEconomy$collectedStack = ItemStack.EMPTY;
        if (stack.isEmpty() || !(((FloatingEntityAccessor) this).betterContentEconomy$getOwner() instanceof ServerPlayer player)) return;
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && (CurrencyIdentity.fromItemId(id) != null
                || ("malum".equals(id.getNamespace()) && id.getPath().endsWith("_spirit")))) {
            MinecraftForge.EVENT_BUS.post(new SpiritAcquiredEvent(player, id, stack.getCount(), self.getUUID()));
        }
    }
}

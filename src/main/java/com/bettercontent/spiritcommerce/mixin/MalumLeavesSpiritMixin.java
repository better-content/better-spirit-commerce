package com.bettercontent.spiritcommerce.mixin;

import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.sammy.malum.common.block.nature.MalumHangingLeavesBlock;
import com.sammy.malum.common.block.nature.MalumLeavesBlock;
import com.sammy.malum.registry.common.item.ItemRegistry;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Malum's leaf-color interaction consumes the economy's Infernal counterpart. */
@Mixin({MalumLeavesBlock.class, MalumHangingLeavesBlock.class})
abstract class MalumLeavesSpiritMixin {
    @Redirect(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/registries/RegistryObject;get()Ljava/lang/Object;", remap = false))
    private Object betterContentEconomy$useImpactSpirit(final RegistryObject<?> registry) {
        return registry == ItemRegistry.INFERNAL_SPIRIT
                ? CurrencyItems.item(CurrencyIdentity.POWER).get() : registry.get();
    }
}

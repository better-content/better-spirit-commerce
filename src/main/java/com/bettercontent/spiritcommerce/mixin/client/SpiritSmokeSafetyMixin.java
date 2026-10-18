package com.bettercontent.spiritcommerce.mixin.client;

import com.bettercontent.spiritcommerce.trader.SpiritCampfireBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only spirit-campfire smoke is cosmetic. Ordinary chimney poisoning remains unchanged. */
@Pseudo
@Mixin(targets = "com.endertech.minecraft.mods.adchimneys.particles.AbstractSmokeParticle$Instance", remap = false)
public abstract class SpiritSmokeSafetyMixin {
    @Shadow @Final protected BlockPos startPos;
    @Unique private boolean betterSpiritCommerce$harmless;
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void betterSpiritCommerce$rememberSource(CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        betterSpiritCommerce$harmless = level != null && level.hasChunkAt(startPos)
                && level.getBlockState(startPos).getBlock() instanceof SpiritCampfireBlock;
    }
    @Inject(method = "updatePoisonEffect", at = @At("HEAD"), cancellable = true, remap = false)
    private void betterSpiritCommerce$cosmeticSmoke(CallbackInfo ci) {
        if (betterSpiritCommerce$harmless) ci.cancel();
    }
}

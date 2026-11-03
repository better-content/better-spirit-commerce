package com.bettercontent.spiritcommerce.mixin;

import com.bettercontent.spiritcommerce.config.EconomyConfig;
import com.bettercontent.spiritcommerce.trader.WanderingTraderVisits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WanderingTraderSpawner.class)
public abstract class WanderingTraderSpawnerMixin {
    @Shadow
    @Final
    private ServerLevelData serverLevelData;

    @Shadow
    protected abstract boolean spawn(ServerLevel level);

    @Shadow @Final private net.minecraft.util.RandomSource random;

    @Inject(method = "findSpawnPositionNear", at = @At("HEAD"), cancellable = true, require = 1)
    private void betterContentEconomy$loadedLocalSite(net.minecraft.world.level.LevelReader reader,
            net.minecraft.core.BlockPos anchor, int radius,
            CallbackInfoReturnable<net.minecraft.core.BlockPos> callback) {
        if (!(reader instanceof ServerLevel level)) { callback.setReturnValue(null); return; }
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = anchor.getX() + random.nextInt(radius * 2) - radius;
            int z = anchor.getZ() + random.nextInt(radius * 2) - radius;
            // Include collision/camp margins before consulting the heightmap or native block checks.
            if (!level.hasChunksAt(new net.minecraft.core.BlockPos(x - 6, anchor.getY(), z - 6),
                    new net.minecraft.core.BlockPos(x + 6, anchor.getY(), z + 6))) continue;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
            if (Math.abs(y - anchor.getY()) > 16 || !level.getWorldBorder().isWithinBounds(pos)) continue;
            if (net.minecraft.world.level.NaturalSpawner.isSpawnPositionOk(
                    net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND, reader, pos,
                    net.minecraft.world.entity.EntityType.WANDERING_TRADER)) {
                callback.setReturnValue(pos);
                return;
            }
        }
        callback.setReturnValue(null);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void betterContentEconomy$runRecurringVisitSchedule(
            final ServerLevel level,
            final boolean spawnEnemies,
            final boolean spawnFriendlies,
            final CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(EconomyConfig.wanderingTraderRecurringVisits()
                ? WanderingTraderVisits.tickScheduledVisit(level, serverLevelData, this::spawn) : 0);
    }

    @ModifyConstant(method = "spawn", constant = @Constant(intValue = 10), require = 1)
    private int betterContentEconomy$removeVanillaOneInTenGate(final int vanillaBound) {
        return 1;
    }

    @Redirect(method = "spawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getRandomPlayer()Lnet/minecraft/server/level/ServerPlayer;"), require = 1)
    private ServerPlayer betterContentEconomy$choosePlayerWithTradingFunds(final ServerLevel level) {
        return WanderingTraderVisits.randomEligiblePlayer(level);
    }
}

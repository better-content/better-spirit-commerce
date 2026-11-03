package com.bettercontent.spiritcommerce.trader;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Loaded-only camp scenery management. Obsolete unloaded fires self-remove via their persisted owner. */
final class TraderCampFireService {
    private TraderCampFireService() { }
    static void remove(ServerLevel level, TraderCampData camp, UUID trader) {
        BlockPos pos = camp.firePos();
        if (pos != null && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof SpiritCampfireBlockEntity fire && fire.ownedBy(trader)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        camp.setFirePos(null);
    }
    static void maintain(WanderingTrader trader) {
        if (!(trader.level() instanceof ServerLevel level) || !TraderCampService.isActiveScheduledTrader(trader)) return;
        TraderCampData camp = TraderCampData.forTrader(trader);
        if (!camp.belongsTo(trader.getUUID())) return;
        if (!TraderCampService.hasReachedCamp(trader)) {
            if (camp.firePos() != null) remove(level, camp, trader.getUUID());
            return;
        }
        if (!level.hasChunkAt(camp.targetPos())
                || !(level.getBlockEntity(camp.targetPos()) instanceof TraderCampPostBlockEntity post)
                || !trader.getUUID().equals(post.traderId())) return;
        if (camp.firePos() != null) {
            if (!level.hasChunkAt(camp.firePos())) return;
            if (level.getBlockEntity(camp.firePos()) instanceof SpiritCampfireBlockEntity fire
                    && fire.ownedBy(trader.getUUID())) return;
            camp.setFirePos(null); // A player's replacement is not ours and must never be removed.
        }
        WanderingTraderTheme theme = WanderingTraderTheme.fromId(post.themeId());
        if (theme == null) return;
        Direction facing = camp.facing();
        BlockPos standing = camp.targetPos().relative(facing);
        List<BlockPos> candidates = List.of(standing.relative(facing.getClockWise(), 2),
                standing.relative(facing.getCounterClockWise(), 2), standing.relative(facing, 2));
        for (BlockPos pos : candidates) {
            if (!level.hasChunksAt(pos.below(), pos.above()) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())
                    || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                    || !level.getFluidState(pos).isEmpty()
                    || !level.getEntities(trader, new AABB(pos)).isEmpty()) continue;
            BlockState fireState = SpiritCampfires.block(theme.spirit()).defaultBlockState()
                    .setValue(SpiritCampfireBlock.FACING, facing);
            if (!level.setBlock(pos, fireState, 3)) continue;
            if (!(level.getBlockEntity(pos) instanceof SpiritCampfireBlockEntity fire)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                continue;
            }
            fire.bind(trader);
            camp.setFirePos(pos);
            return;
        }
    }
}

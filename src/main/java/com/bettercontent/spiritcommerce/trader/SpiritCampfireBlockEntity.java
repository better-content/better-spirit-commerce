package com.bettercontent.spiritcommerce.trader;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/** Ownership never enters item loot. Unloaded obsolete fires validate themselves when loaded again. */
public final class SpiritCampfireBlockEntity extends CampfireBlockEntity {
    private UUID campOwner;
    private boolean fair;
    private long fairStart;
    private long fairEnd;
    private long fairOccurrence;
    public SpiritCampfireBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }
    @Override public BlockEntityType<?> getType() { return SpiritCampfires.ENTITY.get(); }
    public boolean isCampOwned() { return campOwner != null; }
    public boolean ownedBy(UUID id) { return id.equals(campOwner); }
    public void bind(net.minecraft.world.entity.npc.WanderingTrader trader) {
        campOwner = trader.getUUID();
        fair = SpiritFairVisits.isFair(trader);
        fairStart = trader.getPersistentData().getLong(SpiritFairVisits.START_TAG);
        fairEnd = trader.getPersistentData().getLong(SpiritFairVisits.END_TAG);
        fairOccurrence = trader.getPersistentData().getLong(SpiritFairVisits.OCCURRENCE_TAG);
        setChanged();
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        campOwner = tag.hasUUID("CampOwner") ? tag.getUUID("CampOwner") : null;
        fair = tag.getBoolean("SpiritFair");
        fairStart = tag.getLong("FairStart");
        fairEnd = tag.getLong("FairEnd");
        fairOccurrence = tag.getLong("FairOccurrence");
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (campOwner != null) tag.putUUID("CampOwner", campOwner);
        tag.putBoolean("SpiritFair", fair);
        if (fair) {
            tag.putLong("FairStart", fairStart);
            tag.putLong("FairEnd", fairEnd);
            tag.putLong("FairOccurrence", fairOccurrence);
        }
    }
    boolean validCamp(ServerLevel level) {
        if (campOwner == null) return true;
        if (fair) {
            long time = level.getServer().overworld().getDayTime();
            if (time < fairStart || time >= fairEnd || SpiritFairVisits.occurrenceEnded(level, fairOccurrence)
                    || !TraderCampData.hasFairVisit(level, campOwner)) return false;
        }
        TraderCampData data = fair ? TraderCampData.fair(level, campOwner) : TraderCampData.get(level);
        if (!data.belongsTo(campOwner) || !level.dimension().location().toString().equals(data.dimension())
                || !worldPosition.equals(data.firePos())) return false;
        if (level.hasChunkAt(data.targetPos())) {
            return level.getBlockEntity(data.targetPos()) instanceof TraderCampPostBlockEntity post
                    && campOwner.equals(post.traderId()) && !post.expireFairIfNeeded();
        }
        return true;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, SpiritCampfireBlockEntity fire) {
        if (level.isClientSide) {
            // Advanced Chimneys owns smoke when installed; never also emit vanilla/duplicate smoke.
            if (!ModList.get().isLoaded("adchimneys") && state.getValue(CampfireBlock.LIT) && level.random.nextInt(5) == 0) {
                var kind = ((SpiritCampfireBlock) state.getBlock()).kind();
                level.addParticle(SpiritCampfires.SMOKE.get(kind).get(), pos.getX() + .5,
                        pos.getY() + .6, pos.getZ() + .5, 0, .035, 0);
            }
            return;
        }
        if (level.getGameTime() % 20 == 0 && !fire.validCamp((ServerLevel) level)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return;
        }
        if (state.getValue(CampfireBlock.LIT)) CampfireBlockEntity.cookTick(level, pos, state, fire);
        else CampfireBlockEntity.cooldownTick(level, pos, state, fire);
    }
}

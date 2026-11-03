package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Harmless spirit fire. Native campfire cooking, waterlogging and relighting are retained. */
public final class SpiritCampfireBlock extends CampfireBlock {
    private final SpiritKind kind;
    SpiritCampfireBlock(SpiritKind kind) {
        super(false, 0, BlockBehaviour.Properties.copy(Blocks.SOUL_CAMPFIRE));
        this.kind = kind;
    }
    public SpiritKind kind() { return kind; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpiritCampfireBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, SpiritCampfires.ENTITY.get(), SpiritCampfireBlockEntity::tick);
    }
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // Decorative spirit fire must not damage its seller or customers (including purchased fires).
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SpiritCampfireBlockEntity fire
                && fire.isCampOwned()) return List.of();
        return super.getDrops(state, builder);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (random.nextInt(10) == 0) level.playLocalSound(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                net.minecraft.sounds.SoundEvents.CAMPFIRE_CRACKLE, net.minecraft.sounds.SoundSource.BLOCKS,
                .5f + random.nextFloat(), .6f + random.nextFloat() * .7f, false);
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + .25 + random.nextDouble() * .5;
            double z = pos.getZ() + .25 + random.nextDouble() * .5;
            level.addParticle(SpiritCampfires.FLAMES.get(kind).get(), x, pos.getY() + .35, z,
                    (random.nextDouble() - .5) * .012, .012 + random.nextDouble() * .015,
                    (random.nextDouble() - .5) * .012);
        }
    }
}

package com.bettercontent.spiritcommerce.trader;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class TraderCampPostBlockEntity extends BlockEntity {
    static final String TRADER_TAG = "Trader";
    static final String THEME_TAG = "Theme";
    @Nullable private UUID traderId;
    private String themeId = "sacred";

    public TraderCampPostBlockEntity(final BlockPos pos, final BlockState state) {
        super(TraderCampRegistries.POST_ENTITY.get(), pos, state);
    }

    public void bind(final WanderingTrader trader, final WanderingTraderTheme theme) {
        traderId = trader.getUUID();
        themeId = theme.id();
        setChangedAndSync();
    }

    @Nullable public UUID traderId() { return traderId; }
    public String themeId() { return themeId; }

    public CompoundTag saveForItem() {
        CompoundTag tag = new CompoundTag();
        if (traderId != null) tag.putUUID(TRADER_TAG, traderId);
        tag.putString(THEME_TAG, themeId);
        return tag;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag) {
        super.saveAdditional(tag);
        tag.merge(saveForItem());
    }

    @Override
    public void load(final CompoundTag tag) {
        super.load(tag);
        traderId = tag.hasUUID(TRADER_TAG) ? tag.getUUID(TRADER_TAG) : null;
        if (tag.contains(THEME_TAG)) themeId = tag.getString(THEME_TAG);
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    public void retargetLinkedTrader() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel) || traderId == null) return;
        if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
            traderId = null;
            setChangedAndSync();
            return;
        }
        Entity entity = serverLevel.getEntity(traderId);
        if (entity == null) {
            TraderCampData.get(serverLevel).setTarget(traderId,
                    serverLevel.dimension().location().toString(), worldPosition,
                    getBlockState().getValue(TraderCampPostBlock.FACING));
            return;
        }
        if (!(entity instanceof WanderingTrader trader) || !trader.isAlive()
                || !TraderCampService.isActiveScheduledTrader(trader)) {
            traderId = null;
            setChangedAndSync();
            return;
        }
        TraderCampService.retarget(trader, worldPosition, getBlockState().getValue(TraderCampPostBlock.FACING));
        bind(trader, WanderingTraderTheme.fromId(themeId) == null
                ? WanderingTraderTheme.forUuid(trader.getUUID()) : WanderingTraderTheme.fromId(themeId));
    }

    @Override
    public AABB getRenderBoundingBox() {
        // Forge's default box follows the one-block collision mast, not the rendered canopy.
        return renderBounds(worldPosition);
    }

    public static AABB renderBounds(final BlockPos pos) {
        return new AABB(pos).inflate(2.2D, 2.2D, 2.2D);
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}

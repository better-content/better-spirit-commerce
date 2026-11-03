package com.bettercontent.spiritcommerce.trader;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable target state lets a moved post retarget a trader whose old chunk is unloaded. */
public final class TraderCampData extends SavedData {
    private static final String DATA_NAME = "better_spirit_commerce_trader_camp";
    @Nullable private UUID traderId;
    private String dimension = "minecraft:overworld";
    private long targetPos;
    private String facing = Direction.NORTH.getName();
    @Nullable private BlockPos firePos;
    private final java.util.Map<UUID, TraderCampData> visits = new java.util.HashMap<>();
    private TraderCampData owner;
    @Nullable private UUID anchorOwner;
    private long anchorPos;
    private long expiresAt;

    static TraderCampData ordinary(ServerLevel level, UUID trader) {
        TraderCampData root = get(level);
        if (root.belongsTo(trader)) return root;
        return root.visits.computeIfAbsent(trader, id -> {
            TraderCampData visit = new TraderCampData();
            visit.owner = root;
            return visit;
        });
    }

    static boolean hasOrdinaryVisit(ServerLevel level, UUID trader) {
        TraderCampData root = get(level);
        TraderCampData visit = root.visits.get(trader);
        return root.belongsTo(trader) || visit != null && visit.belongsTo(trader);
    }

    static java.util.List<UUID> ordinaryTraderIds(ServerLevel level) {
        TraderCampData root = get(level);
        java.util.List<UUID> ids = new java.util.ArrayList<>(root.visits.keySet());
        if (root.traderId != null) ids.add(root.traderId);
        return ids;
    }

    void begin(UUID player, BlockPos anchor, long expiry) {
        anchorOwner = player;
        anchorPos = anchor.asLong();
        expiresAt = expiry;
        setDirty();
        if (owner != null) owner.setDirty();
    }

    boolean expired(long time) { return expiresAt > 0 && time >= expiresAt; }
    long expiresAt() { return expiresAt; }
    @Nullable UUID anchorOwner() { return anchorOwner; }
    boolean near(BlockPos pos) {
        return BlockPos.of(anchorPos).distSqr(pos) <= 256.0D * 256.0D
                || targetPos().distSqr(pos) <= 256.0D * 256.0D;
    }

    public static TraderCampData get(final ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                TraderCampData::load, TraderCampData::new, DATA_NAME);
    }

    public static TraderCampData forTrader(final net.minecraft.world.entity.npc.WanderingTrader trader) {
        ServerLevel level = (ServerLevel) trader.level();
        return SpiritFairVisits.isFair(trader) ? fair(level, trader.getUUID()) : ordinary(level, trader.getUUID());
    }

    static TraderCampData fair(final ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                TraderCampData::load, TraderCampData::new, DATA_NAME + "_spirit_fair");
    }

    static boolean hasFairVisit(final ServerLevel level, final UUID trader) {
        TraderCampData visit = fair(level).visits.get(trader);
        return visit != null && visit.belongsTo(trader);
    }

    static java.util.List<UUID> fairTraderIds(final ServerLevel level) {
        return java.util.List.copyOf(fair(level).visits.keySet());
    }

    static TraderCampData fair(final ServerLevel level, final UUID trader) {
        TraderCampData root = fair(level);
        return root.visits.computeIfAbsent(trader, id -> {
            TraderCampData visit = new TraderCampData();
            visit.owner = root;
            return visit;
        });
    }

    public static TraderCampData load(final CompoundTag tag) {
        TraderCampData data = new TraderCampData();
        if (tag.hasUUID("Trader")) data.traderId = tag.getUUID("Trader");
        if (tag.contains("Dimension")) data.dimension = tag.getString("Dimension");
        data.targetPos = tag.getLong("TargetPos");
        data.anchorOwner = tag.hasUUID("AnchorOwner") ? tag.getUUID("AnchorOwner") : null;
        data.anchorPos = tag.getLong("AnchorPos");
        data.expiresAt = tag.getLong("ExpiresAt");
        if (tag.contains("FirePos", net.minecraft.nbt.Tag.TAG_LONG)) data.firePos = BlockPos.of(tag.getLong("FirePos"));
        if (tag.contains("Facing")) data.facing = tag.getString("Facing");
        for (var entry : tag.getList("Visits", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag stored = (CompoundTag) entry;
            if (!stored.hasUUID("VisitId")) continue;
            TraderCampData visit = load(stored);
            visit.owner = data;
            data.visits.put(stored.getUUID("VisitId"), visit);
        }
        return data;
    }

    @Override
    public CompoundTag save(final CompoundTag tag) {
        if (traderId != null) tag.putUUID("Trader", traderId);
        tag.putString("Dimension", dimension);
        tag.putLong("TargetPos", targetPos);
        if (anchorOwner != null) tag.putUUID("AnchorOwner", anchorOwner);
        tag.putLong("AnchorPos", anchorPos);
        tag.putLong("ExpiresAt", expiresAt);
        if (firePos != null) tag.putLong("FirePos", firePos.asLong());
        tag.putString("Facing", facing);
        net.minecraft.nbt.ListTag storedVisits = new net.minecraft.nbt.ListTag();
        visits.forEach((id, visit) -> {
            CompoundTag stored = visit.save(new CompoundTag());
            stored.putUUID("VisitId", id);
            storedVisits.add(stored);
        });
        if (!storedVisits.isEmpty()) tag.put("Visits", storedVisits);
        return tag;
    }

    public void setTarget(final UUID trader, final String dimension, final BlockPos pos, final Direction facing) {
        if (!trader.equals(traderId) || !dimension.equals(this.dimension) || pos.asLong() != targetPos
                || !facing.getName().equals(this.facing)) firePos = null;
        traderId = trader;
        this.dimension = dimension;
        targetPos = pos.asLong();
        this.facing = facing.getName();
        setDirty();
        if (owner != null) owner.setDirty();
    }

    @Nullable public BlockPos firePos() { return firePos; }
    public void setFirePos(@Nullable BlockPos pos) {
        firePos = pos == null ? null : pos.immutable();
        setDirty();
        if (owner != null) owner.setDirty();
    }

    public boolean belongsTo(final UUID trader) { return trader.equals(traderId); }
    public String dimension() { return dimension; }
    public BlockPos targetPos() { return BlockPos.of(targetPos); }

    public Direction facing() {
        Direction parsed = Direction.byName(facing);
        return parsed != null && parsed.getAxis().isHorizontal() ? parsed : Direction.NORTH;
    }

    public void clear(final UUID trader) {
        if (belongsTo(trader) || owner != null && owner.visits.get(trader) == this) {
            traderId = null;
            firePos = null;
            setDirty();
            if (owner != null) {
                owner.visits.remove(trader);
                owner.setDirty();
            }
        }
    }
}

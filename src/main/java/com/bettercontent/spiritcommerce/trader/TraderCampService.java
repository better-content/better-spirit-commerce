package com.bettercontent.spiritcommerce.trader;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

/** Server-side creation, relocation, inventory closure, and AI setup for scheduled trader camps. */
@Mod.EventBusSubscriber(modid = "better_spirit_commerce")
public final class TraderCampService {
    static final String CAMP_POS_TAG = "better_spirit_commerce:camp_pos";
    static final String CAMP_FACING_TAG = "better_spirit_commerce:camp_facing";
    private static final String CHECK_TICK_TAG = "better_spirit_commerce:camp_check_tick";
    private static final String STUCK_TICKS_TAG = "better_spirit_commerce:camp_stuck_ticks";
    private static final int STOCK_CHECK_INTERVAL = 20;
    private static final int MOVE_FALLBACK_TICKS = 600;

    private TraderCampService() { }

    public static boolean createForVisit(final WanderingTrader trader, final WanderingTraderTheme theme) {
        return createForVisit(trader, theme, false);
    }

    static boolean createForVisit(final WanderingTrader trader, final WanderingTraderTheme theme, final boolean fair) {
        if (!(trader.level() instanceof ServerLevel level)) return false;
        Direction facing = trader.getDirection();
        BlockPos candidate = findPostPosition(level, trader.blockPosition(), facing, fair);
        if (candidate == null) return false;
        BlockState state = TraderCampRegistries.POST.get().defaultBlockState().setValue(TraderCampPostBlock.FACING, facing);
        if (!level.setBlock(candidate, state, 3)) return false;
        if (!(level.getBlockEntity(candidate) instanceof TraderCampPostBlockEntity post)) {
            level.setBlock(candidate, Blocks.AIR.defaultBlockState(), 3);
            return false;
        }
        if (fair) trader.getPersistentData().putBoolean(SpiritFairVisits.FAIR_TAG, true);
        post.bind(trader, theme);
        retarget(trader, candidate, facing);
        (fair ? TraderCampData.fair(level, trader.getUUID()) : TraderCampData.get(level))
                .setTarget(trader.getUUID(), level.dimension().location().toString(), candidate, facing);
        installCampGoals(trader);
        return true;
    }

    private static BlockPos findPostPosition(final ServerLevel level, final BlockPos traderPos, final Direction facing,
                                             final boolean fair) {
        Direction side = facing.getClockWise();
        for (int distance = 2; distance <= 4; distance++) {
            for (int sideways : List.of(0, -1, 1, -2, 2)) {
                BlockPos pos = traderPos.relative(facing.getOpposite(), distance).relative(side, sideways);
                if (level.hasChunksAt(pos.offset(-3, -1, -3), pos.offset(3, 3, 3))
                        && level.getWorldBorder().isWithinBounds(pos)
                        && level.isEmptyBlock(pos) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                        && (!fair || level.isEmptyBlock(pos.relative(facing)) && level.isEmptyBlock(pos.relative(facing).above())
                                && level.getBlockState(pos.relative(facing).below()).isValidSpawn(
                                level, pos.relative(facing).below(), net.minecraft.world.entity.EntityType.WANDERING_TRADER))
                        && level.isEmptyBlock(pos.above())
                        && level.noCollision(null, new AABB(pos).inflate(1.6D, 0.0D, 1.6D))) {
                    return pos;
                }
            }
        }
        return null;
    }

    public static void restoreOnJoin(final WanderingTrader trader) {
        if (!(trader.level() instanceof ServerLevel level) || !isActiveScheduledTrader(trader)) return;
        if (SpiritFairVisits.isFair(trader) && !SpiritFairVisits.maintain(trader)) return;
        TraderCampData data = TraderCampData.forTrader(trader);
        if (!data.belongsTo(trader.getUUID()) || !data.dimension().equals(level.dimension().location().toString())) return;
        retarget(trader, data.targetPos(), data.facing());
        installCampGoals(trader);
    }

    public static boolean isActiveScheduledTrader(final WanderingTrader trader) {
        if (!(trader.level() instanceof ServerLevel level)) return false;
        if (SpiritFairVisits.isFair(trader)) return true;
        UUID active = WanderingTraderScheduleData.get(level.getServer().overworld()).activeTraderId();
        return trader.getUUID().equals(active);
    }

    static void retarget(final WanderingTrader trader, final BlockPos post, final Direction facing) {
        if (trader.getPersistentData().contains(CAMP_POS_TAG)
                && (trader.getPersistentData().getLong(CAMP_POS_TAG) != post.asLong() || facing(trader) != facing)) {
            TraderCampFireService.remove((ServerLevel) trader.level(), TraderCampData.forTrader(trader), trader.getUUID());
        }
        trader.getPersistentData().putLong(CAMP_POS_TAG, post.asLong());
        trader.getPersistentData().putString(CAMP_FACING_TAG, facing.getName());
        trader.getPersistentData().remove(STUCK_TICKS_TAG);
        trader.getPersistentData().remove(CHECK_TICK_TAG);
    }

    static BlockPos targetPosition(final WanderingTrader trader) {
        return BlockPos.of(trader.getPersistentData().getLong(CAMP_POS_TAG)).relative(facing(trader));
    }

    static Direction facing(final WanderingTrader trader) {
        Direction direction = Direction.byName(trader.getPersistentData().getString(CAMP_FACING_TAG));
        return direction != null && direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
    }

    private static void installCampGoals(final WanderingTrader trader) {
        boolean present = trader.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof TraderCampGoal);
        if (present) return;
        for (WrappedGoal wrapped : List.copyOf(trader.goalSelector.getAvailableGoals())) {
            String goalName = wrapped.getGoal().getClass().getName();
            if (wrapped.getGoal() instanceof RandomStrollGoal || goalName.endsWith("$WanderToPositionGoal")) {
                trader.goalSelector.removeGoal(wrapped.getGoal());
            }
        }
        trader.goalSelector.addGoal(2, new TraderCampGoal(trader));
    }

    @SubscribeEvent
    public static void onTraderJoin(final EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof WanderingTrader trader && !event.getLevel().isClientSide()) {
            restoreOnJoin(trader);
            if (trader.isRemoved()) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPostPlaced(final BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getBlockEntity(event.getPos()) instanceof TraderCampPostBlockEntity post)) return;
        post.retargetLinkedTrader();
    }

    @SubscribeEvent
    public static void onTraderDeath(final LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)
                || !(trader.level() instanceof ServerLevel level) || !isActiveScheduledTrader(trader)) return;
        depart(trader, level);
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % STOCK_CHECK_INTERVAL != 0) return;
        ServerLevel level = event.getServer().overworld();
        UUID traderId = WanderingTraderScheduleData.get(level).activeTraderId();
        Entity entity = traderId == null ? null : level.getEntity(traderId);
        if (!(entity instanceof WanderingTrader trader) || !trader.isAlive()) return;
        trader.setDespawnDelay(48_000);
        if (stockEmpty(trader, level)) depart(trader, level);
    }

    public static boolean stockEmpty(final WanderingTrader trader, final ServerLevel level) {
        var offers = trader.getOffers();
        return !offers.isEmpty() && offers.stream().allMatch(offer ->
                AuthoredMerchantStock.availableTrades(trader, offer, level,
                        Math.max(0, offer.getMaxUses() - offer.getUses())) == 0);
    }

    static void depart(final WanderingTrader trader, final ServerLevel level) {
        TraderCampData camp = TraderCampData.forTrader(trader);
        boolean fair = SpiritFairVisits.isFair(trader);
        TraderCampFireService.remove(level, camp, trader.getUUID());
        BlockPos postPos = fair && camp.belongsTo(trader.getUUID()) ? camp.targetPos()
                : BlockPos.of(trader.getPersistentData().getLong(CAMP_POS_TAG));
        ServerLevel campLevel = fair ? level.getServer().overworld() : level;
        if (trader.getPersistentData().contains(CAMP_POS_TAG) && (!fair || campLevel.hasChunkAt(postPos))
                && campLevel.getBlockEntity(postPos) instanceof TraderCampPostBlockEntity post
                && trader.getUUID().equals(post.traderId())) {
            campLevel.setBlock(postPos, Blocks.AIR.defaultBlockState(), 3);
        }
        camp.clear(trader.getUUID());
        if (!SpiritFairVisits.isFair(trader)) WanderingTraderScheduleData.get(level).clearActiveTrader();
        trader.discard();
    }

    static boolean hasReachedCamp(final WanderingTrader trader) {
        return trader.distanceToSqr(Vec3.atCenterOf(targetPosition(trader))) <= 2.25D;
    }

    static boolean moveToCamp(final WanderingTrader trader) {
        if (!(trader.level() instanceof ServerLevel level)) return false;
        BlockPos target = targetPosition(trader);
        for (BlockPos candidate : List.of(target, target.north(), target.south(), target.east(), target.west())) {
            if (level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), Direction.UP)
                    && level.isEmptyBlock(candidate) && level.isEmptyBlock(candidate.above())
                    && level.noCollision(trader, trader.getBoundingBox().move(
                            candidate.getX() + 0.5D - trader.getX(), candidate.getY() - trader.getY(),
                            candidate.getZ() + 0.5D - trader.getZ()))) {
                trader.moveTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D,
                        trader.getYRot(), trader.getXRot());
                trader.getNavigation().stop();
                return true;
            }
        }
        return false;
    }
}

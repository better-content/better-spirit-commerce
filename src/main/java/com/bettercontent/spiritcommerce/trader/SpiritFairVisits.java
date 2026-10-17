package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.config.EconomyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One additional visit per server occurrence, independent of the ordinary trader schedule. */
@Mod.EventBusSubscriber(modid = "better_spirit_commerce")
public final class SpiritFairVisits {
    static final String FAIR_TAG = "better_spirit_commerce:spirit_fair";
    static final String START_TAG = "better_spirit_commerce:fair_start";
    static final String END_TAG = "better_spirit_commerce:fair_end";
    static final String OCCURRENCE_TAG = "better_spirit_commerce:fair_occurrence";

    private SpiritFairVisits() { }

    record Window(long occurrence, long scheduledDay) { }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel level = event.getServer().overworld();
        // UUID lookup never loads chunks. Include loaded, non-ticking entities and older
        // forced occurrences, even when the optional provider was removed on restart.
        for (var id : TraderCampData.fairTraderIds(level)) {
            TraderCampData camp = TraderCampData.fair(level, id);
            if (camp.belongsTo(id) && level.hasChunkAt(camp.targetPos())
                    && level.getBlockEntity(camp.targetPos()) instanceof TraderCampPostBlockEntity post) {
                post.expireFairIfNeeded();
            }
            for (ServerLevel dimension : event.getServer().getAllLevels()) {
                if (dimension.getEntity(id) instanceof WanderingTrader trader) {
                    maintain(trader, event.getServer().getTickCount() % 20 == 0);
                }
            }
        }
        tick(level, SpiritFairBridge.snapshot(event.getServer()));
    }

    static boolean tick(final ServerLevel level, final Window window) {
        return tick(level, window, level.players());
    }

    static boolean tick(final ServerLevel level, final Window window, final java.util.List<ServerPlayer> players) {
        if (window == null || !level.dimension().equals(Level.OVERWORLD)) return false;
        long time = level.getDayTime();
        long start = window.scheduledDay() * 24000L + 12000L;
        long end = (window.scheduledDay() + 1L) * 24000L;
        if (time < start || time >= end) return false;
        SpiritFairData data = SpiritFairData.get(level);
        if (!data.canAttempt(window.occurrence(), level.getGameTime())) return false;
        // Even failed/disabled/no-player searches are bounded to once per minute.
        data.retryAfter(level.getGameTime());
        if (!level.getGameRules().getBoolean(GameRules.RULE_DO_TRADER_SPAWNING)
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return false;
        for (ServerPlayer player : players) {
            if (player.level() != level) continue;
            if (!player.isAlive() || player.isSpectator()) continue;
            WanderingTrader trader = prepareNear(level, player, data.nextTheme(), start, end, window.occurrence());
            if (trader == null) continue;
            WanderingTraderTheme theme = data.nextTheme();
            SpiritFairData.Admission admission = data.admit(window.occurrence());
            // Flush terminal admission BEFORE exposing an entity to join/save callbacks.
            // A crash after this point can lose the visit, but can never mint a second stock.
            level.getDataStorage().save();
            if (!level.addFreshEntity(trader)) {
                boolean neverInserted = !trader.isAddedToWorld() && level.getEntity(trader.getUUID()) == null;
                TraderCampService.depart(trader, level);
                if (neverInserted) {
                    data.rollbackUnexposed(admission);
                    level.getDataStorage().save();
                }
                return false;
            }
            data.complete(trader.getUUID());
            level.getDataStorage().save();
            if (EconomyConfig.wanderingTraderAnnounceArrival()) {
                WanderingTraderVisits.announceArrival(level, trader, theme);
            }
            return true;
        }
        return false;
    }

    private static WanderingTrader prepareNear(final ServerLevel level, final ServerPlayer player,
                                               final WanderingTraderTheme theme, final long start, final long end,
                                               final long occurrence) {
        // Bounded deterministic ring, never generate or load terrain to stage a camp.
        for (int radius : new int[] {16, 24, 32}) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos column = player.blockPosition().relative(direction, radius);
                if (!level.hasChunkAt(column)) continue;
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                if (!level.hasChunksAt(pos.offset(-8, -2, -8), pos.offset(8, 4, 8))
                        || !level.getWorldBorder().isWithinBounds(pos)
                        || Math.abs(pos.getY() - player.getBlockY()) > 16
                        || !level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), EntityType.WANDERING_TRADER)
                        || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                        || !level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) continue;
                WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
                if (trader == null) return null;
                trader.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
                if (!level.noCollision(trader)) continue;
                WanderingTraderVisits.applyTheme(trader, theme, true);
                trader.getPersistentData().putBoolean(FAIR_TAG, true);
                trader.getPersistentData().putLong(START_TAG, start);
                trader.getPersistentData().putLong(END_TAG, end);
                trader.getPersistentData().putLong(OCCURRENCE_TAG, occurrence);
                if (TraderCampService.createForVisit(trader, theme, true)) return trader;
            }
        }
        return null;
    }

    static boolean isFair(final WanderingTrader trader) {
        return trader.getPersistentData().getBoolean(FAIR_TAG);
    }

    /** Called on join as well as ticks: unloaded expired visits never regain stock or a lifetime. */
    static boolean maintain(final WanderingTrader trader) {
        return maintain(trader, true);
    }

    static boolean occurrenceEnded(final ServerLevel level, final long occurrence) {
        // During initial world/entity loading the provider has not published its first START tick.
        // Wait until tick 1; forced end/replacement is then authoritative, including same-night changes.
        if (level.getServer().getTickCount() <= 0 || !net.minecraftforge.fml.ModList.get().isLoaded("better_celestial_events")) {
            return false;
        }
        Window current = SpiritFairBridge.snapshot(level.getServer());
        return current == null || current.occurrence() != occurrence;
    }

    private static boolean maintain(final WanderingTrader trader, final boolean checkStock) {
        if (!isFair(trader) || !(trader.level() instanceof ServerLevel level)) return false;
        long time = level.getServer().overworld().getDayTime();
        if (!level.dimension().equals(Level.OVERWORLD)
                || time < trader.getPersistentData().getLong(START_TAG)
                || time >= trader.getPersistentData().getLong(END_TAG)
                || occurrenceEnded(level, trader.getPersistentData().getLong(OCCURRENCE_TAG))
                || !trader.isAlive() || checkStock && TraderCampService.stockEmpty(trader, level)) {
            TraderCampService.depart(trader, level);
            return false;
        }
        trader.setDespawnDelay(48000);
        return true;
    }
}

package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.config.EconomyConfig;
import com.bettercontent.spiritcommerce.api.event.SpiritAcquiredEvent;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import java.util.Comparator;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class WanderingTraderVisits {
    public static final String THEME_TAG = "better_spirit_commerce:wandering_trader_theme";
    static final String EXPIRY_TAG = "better_spirit_commerce:visit_expiry";
    public static final long VISIT_TICKS = 7L * 24_000L;
    public static final int MIN_INTERVAL_TICKS = 5 * 12_000;
    public static final int MAX_INTERVAL_TICKS = 9 * 12_000;
    public static final int PLACEMENT_RETRY_TICKS = 1_200;

    static int nextInterval(net.minecraft.util.RandomSource random) {
        return MIN_INTERVAL_TICKS + random.nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
    }

    private WanderingTraderVisits() {
    }

    @SubscribeEvent
    public static void onSpiritAcquired(final SpiritAcquiredEvent event) {
        final ServerPlayer player = event.getPlayer();
        if (!player.isAlive() || player.isSpectator()) return;
        final ServerLevel overworld = player.serverLevel().getServer().overworld();
        SpiritCollectionHistory.get(overworld).record(player.getUUID(),
                CurrencyIdentity.fromItemId(event.getSpiritId()), event.getCount(), overworld.getGameTime());
    }

    static ServerPlayer collectionAnchor(final ServerLevel level, final WanderingTrader trader) {
        return collectionAnchor(level.players(), trader);
    }

    static ServerPlayer collectionAnchor(final java.util.List<ServerPlayer> players, final WanderingTrader trader) {
        return players.stream()
                .filter(player -> player.isAlive() && !player.isSpectator())
                .min(Comparator.comparingDouble((ServerPlayer player) -> player.distanceToSqr(trader))
                        .thenComparing(ServerPlayer::getUUID))
                .orElse(null);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(final EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader) || event.getLevel().isClientSide()) {
            return;
        }
        final String storedId = trader.getPersistentData().getString(THEME_TAG);
        final WanderingTraderTheme storedTheme = WanderingTraderTheme.fromId(storedId);
        if (storedTheme != null) {
            applyTheme(trader, storedTheme, false);
        } else {
            applyTheme(trader, WanderingTraderTheme.forUuid(trader.getUUID()), false);
        }
        TraderCampService.restoreOnJoin(trader);
        if (trader.isRemoved()) event.setCanceled(true);
    }

    public static int tickScheduledVisit(
            final ServerLevel level,
            final ServerLevelData levelData,
            final SpawnAttempt spawnAttempt) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return 0;
        }
        final GameRules rules = level.getGameRules();
        if (!rules.getBoolean(GameRules.RULE_DO_TRADER_SPAWNING)
                || !rules.getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            return 0;
        }

        final long gameTime = level.getGameTime();
        TraderCampService.expireOrdinaryVisits(level);
        if (level.players().stream().noneMatch(player -> eligibleAnchor(level, player))) return 0;
        final WanderingTraderScheduleData schedule = WanderingTraderScheduleData.get(level);
        if (!schedule.initialized()) schedule.initializeIfNeeded(gameTime, nextInterval(level.random));
        if (!schedule.isVisitDue(gameTime)) return 0;
        // A failed site keeps this arrival pending; never roll another multi-day interval.
        schedule.scheduleRetry(gameTime, PLACEMENT_RETRY_TICKS);
        final UUID previousId = levelData.getWanderingTraderId();
        final java.util.Set<UUID> before = new java.util.HashSet<>();
        level.getAllEntities().forEach(entity -> before.add(entity.getUUID()));
        if (!spawnAttempt.spawn(level)) {
            rollbackSpawn(level, levelData, previousId, before);
            return 0;
        }

        final UUID traderId = levelData.getWanderingTraderId();
        final Entity spawnedEntity = traderId == null ? null : level.getEntity(traderId);
        if (!(spawnedEntity instanceof WanderingTrader trader) || before.contains(traderId)) {
            rollbackSpawn(level, levelData, previousId, before);
            return 0;
        }

        final ServerPlayer anchor = level.players().stream().filter(player -> eligibleAnchor(level, player))
                .filter(player -> player.distanceToSqr(trader) <= 256.0D * 256.0D)
                .min(Comparator.comparingDouble((ServerPlayer player) -> player.distanceToSqr(trader))
                        .thenComparing(ServerPlayer::getUUID)).orElse(null);
        if (anchor == null) {
            rollbackSpawn(level, levelData, previousId, before);
            return 0;
        }
        final WanderingTraderTheme theme = SpiritCollectionHistory.get(level).selectTheme(
                anchor.getUUID(), gameTime, level.random);
        trader.getPersistentData().putLong(EXPIRY_TAG, gameTime + VISIT_TICKS);
        trader.setDespawnDelay((int) VISIT_TICKS);
        applyTheme(trader, theme, true);
        if (!TraderCampService.createForVisit(trader, theme)) {
            rollbackSpawn(level, levelData, previousId, before);
            return 0;
        }
        TraderCampData.forTrader(trader).begin(anchor.getUUID(), anchor.blockPosition(), gameTime + VISIT_TICKS);
        schedule.completeVisit(gameTime, nextInterval(level.random), trader.getUUID());
        if (EconomyConfig.wanderingTraderAnnounceArrival()) {
            announceArrival(level, trader, theme);
        }
        return 1;
    }

    public static ServerPlayer randomEligiblePlayer(final ServerLevel level) {
        final var eligible = level.players().stream()
                .filter(player -> eligibleAnchor(level, player)).toList();
        return eligible.isEmpty() ? null : eligible.get(level.random.nextInt(eligible.size()));
    }

    static boolean eligibleAnchor(ServerLevel level, ServerPlayer player) {
        return !player.isSpectator();
    }

    private static void rollbackSpawn(ServerLevel level, ServerLevelData data, UUID previous,
                                      java.util.Set<UUID> before) {
        UUID id = data.getWanderingTraderId();
        Entity entity = id == null || before.contains(id) ? null : level.getEntity(id);
        if (entity instanceof WanderingTrader trader) {
            // EntityLookup exposes a live fastutil values view. Discard synchronously removes from
            // that lookup and can shift its hash cluster underneath the iterator, skipping a companion
            // (or returning null). Finish discovery before mutating any native entity membership.
            final var createdLlamas = new java.util.ArrayList<net.minecraft.world.entity.animal.horse.TraderLlama>();
            for (Entity candidate : level.getAllEntities()) {
                if (candidate instanceof net.minecraft.world.entity.animal.horse.TraderLlama llama
                        && !before.contains(llama.getUUID())) createdLlamas.add(llama);
            }
            for (var llama : createdLlamas) {
                // Recheck ownership immediately before removal; discovery itself grants no ownership.
                if (!llama.isTamed() && llama.getOwnerUUID() == null && llama.getLeashHolder() == trader) llama.discard();
            }
            TraderCampData.forTrader(trader).clear(trader.getUUID());
            trader.discard();
        }
        data.setWanderingTraderId(previous);
    }

    public static void applyTheme(
            final WanderingTrader trader,
            final WanderingTraderTheme theme,
            final boolean replaceCustomName) {
        trader.getPersistentData().putString(THEME_TAG, theme.id());
        if (replaceCustomName || !trader.hasCustomName()) {
            trader.setCustomName(theme.displayName());
            trader.setCustomNameVisible(false);
        }
    }

    static void announceArrival(
            final ServerLevel level,
            final WanderingTrader trader,
            final WanderingTraderTheme theme) {
        final Component message = Component.translatable(
                "message.better_spirit_commerce.wandering_trader.arrival",
                theme.displayName(),
                level.dimension().location().toString(),
                trader.blockPosition().getX(),
                trader.blockPosition().getY(),
                trader.blockPosition().getZ());
        level.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }

    @FunctionalInterface
    public interface SpawnAttempt {
        boolean spawn(ServerLevel level);
    }
}

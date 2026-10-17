package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.config.EconomyConfig;
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

    private WanderingTraderVisits() {
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
        final WanderingTraderScheduleData schedule = WanderingTraderScheduleData.get(level);
        schedule.initializeIfNeeded(gameTime, EconomyConfig.wanderingTraderInitialDelay());
        if (!schedule.isVisitDue(gameTime)) {
            return 0;
        }
        if (level.players().isEmpty()) {
            schedule.scheduleRetry(gameTime, EconomyConfig.wanderingTraderRetryDelay());
            return 0;
        }

        final UUID activeId = schedule.activeTraderId();
        if (activeId != null) {
            final Entity activeEntity = level.getEntity(activeId);
            if (activeEntity instanceof WanderingTrader && activeEntity.isAlive()) {
                schedule.scheduleRetry(gameTime, EconomyConfig.wanderingTraderRetryDelay());
                return 0;
            }
            schedule.clearActiveTrader();
        }

        if (!spawnAttempt.spawn(level)) {
            schedule.scheduleRetry(gameTime, EconomyConfig.wanderingTraderRetryDelay());
            return 0;
        }

        final UUID traderId = levelData.getWanderingTraderId();
        final Entity spawnedEntity = traderId == null ? null : level.getEntity(traderId);
        if (!(spawnedEntity instanceof WanderingTrader trader)) {
            schedule.scheduleRetry(gameTime, EconomyConfig.wanderingTraderRetryDelay());
            return 0;
        }

        final WanderingTraderTheme theme = schedule.nextTheme();
        applyTheme(trader, theme, true);
        if (!TraderCampService.createForVisit(trader, theme)) {
            trader.discard();
            schedule.scheduleRetry(gameTime, EconomyConfig.wanderingTraderRetryDelay());
            return 0;
        }
        schedule.completeVisit(gameTime, EconomyConfig.wanderingTraderVisitInterval(), trader.getUUID());
        if (EconomyConfig.wanderingTraderAnnounceArrival()) {
            announceArrival(level, trader, theme);
        }
        return 1;
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

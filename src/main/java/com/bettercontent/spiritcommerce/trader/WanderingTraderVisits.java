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
                .filter(player -> !TraderAffordability.affordableThemes(player).isEmpty())
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
        final WanderingTraderScheduleData schedule = WanderingTraderScheduleData.get(level);
        final UUID activeId = schedule.activeTraderId();
        if (activeId != null) {
            final Entity activeEntity = level.getEntity(activeId);
            if (activeEntity instanceof WanderingTrader && activeEntity.isAlive()) return 0;
            // An unloaded linked camp is still an active visit, not permission to duplicate it.
            if (activeEntity == null && TraderCampData.get(level).belongsTo(activeId)) return 0;
            schedule.clearActiveTrader();
        }
        if (level.players().stream().noneMatch(player -> !TraderAffordability.affordableThemes(player).isEmpty())) return 0;
        if (!spawnAttempt.spawn(level)) return 0;

        final UUID traderId = levelData.getWanderingTraderId();
        final Entity spawnedEntity = traderId == null ? null : level.getEntity(traderId);
        if (!(spawnedEntity instanceof WanderingTrader trader)) return 0;

        final ServerPlayer anchor = collectionAnchor(level, trader);
        if (anchor == null) {
            trader.discard();
            return 0;
        }
        final WanderingTraderTheme theme = SpiritCollectionHistory.get(level).selectTheme(
                anchor.getUUID(), gameTime, level.random, TraderAffordability.affordableThemes(anchor));
        applyTheme(trader, theme, true);
        if (!TraderCampService.createForVisit(trader, theme)) {
            trader.discard();
            return 0;
        }
        // Legacy schedule fields remain readable, but no initial, interval or retry clock gates admission.
        schedule.completeVisit(gameTime, 0, trader.getUUID());
        if (EconomyConfig.wanderingTraderAnnounceArrival()) {
            announceArrival(level, trader, theme);
        }
        return 1;
    }

    public static ServerPlayer randomEligiblePlayer(final ServerLevel level) {
        final var eligible = level.players().stream()
                .filter(player -> !TraderAffordability.affordableThemes(player).isEmpty()).toList();
        return eligible.isEmpty() ? null : eligible.get(level.random.nextInt(eligible.size()));
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

package com.bettercontent.spiritcommerce.trader;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.WanderingTrader;

/** Keeps a scheduled trader at its post and walks it to a relocated post. */
final class TraderCampGoal extends Goal {
    private final WanderingTrader trader;
    private int stuckTicks;
    private int repathTicks;
    private double lastDistance = Double.MAX_VALUE;

    TraderCampGoal(final WanderingTrader trader) {
        this.trader = trader;
    }

    @Override
    public boolean canUse() {
        return TraderCampService.isActiveScheduledTrader(trader)
                && trader.getPersistentData().contains(TraderCampService.CAMP_POS_TAG);
    }

    @Override public boolean canContinueToUse() { return canUse(); }

    @Override
    public void tick() {
        if (trader.tickCount % 20 == 0) TraderCampFireService.maintain(trader);
        if (trader.isLeashed()) {
            trader.getNavigation().stop();
            stuckTicks = 0;
            lastDistance = Double.MAX_VALUE;
            return;
        }
        if (TraderCampService.hasReachedCamp(trader)) {
            trader.getNavigation().stop();
            stuckTicks = 0;
            repathTicks = 0;
            return;
        }
        double distance = trader.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(
                TraderCampService.targetPosition(trader)));
        if (distance + 0.04D < lastDistance) {
            stuckTicks = 0;
            lastDistance = distance;
        } else {
            stuckTicks++;
        }
        if (++repathTicks >= 20 || trader.getNavigation().isDone()) {
            repathTicks = 0;
            var target = TraderCampService.targetPosition(trader);
            trader.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.8D);
        }
        if (stuckTicks >= 600 && TraderCampService.moveToCamp(trader)) {
            stuckTicks = 0;
            lastDistance = Double.MAX_VALUE;
        }
    }

    @Override
    public void start() {
        stuckTicks = 0;
        repathTicks = 0;
        lastDistance = Double.MAX_VALUE;
    }

    @Override public void stop() { trader.getNavigation().stop(); }
}

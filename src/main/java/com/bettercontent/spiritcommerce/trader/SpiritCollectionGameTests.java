package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.api.event.SpiritAcquiredEvent;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.sammy.malum.common.entity.spirit.SpiritItemEntity;
import com.sammy.malum.common.item.curiosities.SpiritPouchItem;
import com.sammy.malum.registry.common.item.ItemRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class SpiritCollectionGameTests {
    private SpiritCollectionGameTests() { }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void pickupListenerRecordsOnlyCommerceCurrencyAndSurvivesSpending(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "pickup-history"));
        player.setGameMode(GameType.SURVIVAL);
        long now = level.getServer().overworld().getGameTime();
        var history = SpiritCollectionHistory.get(level);
        MinecraftForge.EVENT_BUS.post(new SpiritAcquiredEvent(player, CurrencyIdentity.WORK.itemId(), 9, UUID.randomUUID()));
        MinecraftForge.EVENT_BUS.post(new SpiritAcquiredEvent(player, new ResourceLocation("malum:arcane_spirit"), 100, UUID.randomUUID()));
        // Exercise the real native collect method and its production mixin, including optional pouch insertion.
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(ItemRegistry.SPIRIT_POUCH.get()));
        player.moveTo(helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getCenter());
        var zombie = helper.spawn(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(2, 2, 2));
        com.sammy.malum.common.capability.MalumLivingEntityDataCapability.getCapability(zombie)
                .soulData.exposedSoulDuration = 100.0F;
        zombie.hurt(level.damageSources().playerAttack(player), 1000.0F);
        var pickups = level.getEntitiesOfClass(SpiritItemEntity.class, zombie.getBoundingBox().inflate(4)).stream()
                .filter(entity -> player.getUUID().equals(entity.ownerUUID))
                .filter(entity -> CurrencyIdentity.fromItemId(net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(entity.getItem().getItem())) != null).toList();
        helper.assertTrue(!pickups.isEmpty(), "Credited native kill must release actual owned commerce pickups");
        int[] expected = new int[CurrencyIdentity.values().length];
        expected[CurrencyIdentity.WORK.ordinal()] = 9;
        for (SpiritItemEntity pickup : pickups) {
            ItemStack original = pickup.getItem().copy();
            CurrencyIdentity identity = CurrencyIdentity.fromItemId(net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getKey(original.getItem()));
            helper.assertTrue(original.getCount() > 0, "Released stack must contain real spirits");
            expected[identity.ordinal()] += original.getCount();
            pickup.owner = player;
            pickup.collect();
            pickup.discard();
        }
        player.getInventory().clearContent();
        var loaded = SpiritCollectionHistory.load(history.save(new CompoundTag()));
        for (CurrencyIdentity identity : CurrencyIdentity.values()) {
            helper.assertTrue(loaded.weights(player.getUUID(), now)[identity.ordinal()] == Math.sqrt(expected[identity.ordinal()]),
                    "Actual native kill/pickup/pouch path must signal each full released count for " + identity.id()
                            + "; expected count=" + expected[identity.ordinal()] + ", observed weight="
                            + loaded.weights(player.getUUID(), now)[identity.ordinal()]);
        }
        player.setGameMode(GameType.SPECTATOR);
        MinecraftForge.EVENT_BUS.post(new SpiritAcquiredEvent(player, CurrencyIdentity.WORK.itemId(), 7, UUID.randomUUID()));
        helper.assertTrue(history.weights(player.getUUID(), now)[CurrencyIdentity.WORK.ordinal()] == Math.sqrt(expected[CurrencyIdentity.WORK.ordinal()]),
                "Spectators must not influence a market");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void tenLooseSpiritsOrOptionalPouchContentsAdmitEarlyVisits(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "market-funds"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        helper.assertTrue(TraderAffordability.affordableThemes(player).isEmpty(), "Empty inventory admitted a visit");
        player.getInventory().setItem(0, new ItemStack(CurrencyItems.item(CurrencyIdentity.WORK).get(), 9));
        helper.assertTrue(TraderAffordability.affordableThemes(player).isEmpty(), "Nine spirits passed the ten-spirit gate");
        player.getInventory().setItem(1, new ItemStack(CurrencyItems.item(CurrencyIdentity.TEMPO).get(), 1));
        helper.assertTrue(TraderAffordability.affordableThemes(player).contains(WanderingTraderTheme.ARCANE),
                "Ten loose mixed spirits must qualify without crafting a pouch");
        helper.assertTrue(!TraderAffordability.affordableThemes(player).contains(WanderingTraderTheme.SACRED),
                "A theme with no matching payment cannot be afforded");
        player.getInventory().clearContent();
        ItemStack pouch = new ItemStack(ItemRegistry.SPIRIT_POUCH.get());
        SpiritPouchItem.getInventory(pouch).setItem(0, new ItemStack(CurrencyItems.item(CurrencyIdentity.WORK).get(), 10));
        player.getInventory().setItem(0, pouch);
        helper.assertTrue(TraderAffordability.affordableThemes(player).contains(WanderingTraderTheme.ARCANE),
                "Optional pouch funds must count just like loose spirits");
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 64));
        helper.assertTrue(TraderAffordability.affordableThemes(player).isEmpty(), "Non-spirit valuables cannot satisfy admission");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void unfundedAdmissionHonorsSavedScheduleRetryAndSpectatorGameruleGates(GameTestHelper helper) {
        var level = helper.getLevel();
        var oldSchedule = WanderingTraderScheduleData.get(level);
        var oldCamp = TraderCampData.get(level);
        var storage = level.getDataStorage();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "market-no-clock"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        var rules = level.getGameRules();
        boolean traderRule = rules.getBoolean(net.minecraft.world.level.GameRules.RULE_DO_TRADER_SPAWNING);
        boolean mobRule = rules.getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
        try {
            var schedule = new WanderingTraderScheduleData();
            storage.set("better_spirit_commerce_wandering_trader", schedule);
            storage.set("better_spirit_commerce_trader_camp", new TraderCampData());
            rules.getRule(net.minecraft.world.level.GameRules.RULE_DO_TRADER_SPAWNING).set(true, level.getServer());
            rules.getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(true, level.getServer());
            level.players().add(player);
            int[] attempts = {0};
            WanderingTraderVisits.SpawnAttempt failedSite = ignored -> { attempts[0]++; return false; };
            var levelData = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
            WanderingTraderVisits.tickScheduledVisit(level, levelData, failedSite);
            long now = level.getGameTime();
            long initial = schedule.nextAttemptGameTime();
            helper.assertTrue(attempts[0] == 0 && initial >= now + WanderingTraderVisits.MIN_INTERVAL_TICKS
                    && initial <= now + WanderingTraderVisits.MAX_INTERVAL_TICKS, "Initial arrival must wait 5–9 days");
            schedule.scheduleRetry(now, 0);
            player.setGameMode(GameType.SPECTATOR);
            WanderingTraderVisits.tickScheduledVisit(level, levelData, failedSite);
            helper.assertTrue(attempts[0] == 0, "Spectator admitted an arrival");
            player.setGameMode(GameType.SURVIVAL);
            for (var rule : List.of(net.minecraft.world.level.GameRules.RULE_DO_TRADER_SPAWNING,
                    net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) {
                rules.getRule(rule).set(false, level.getServer());
                WanderingTraderVisits.tickScheduledVisit(level, levelData, failedSite);
                helper.assertTrue(attempts[0] == 0, "Disabled gamerule admitted an arrival");
                rules.getRule(rule).set(true, level.getServer());
            }
            WanderingTraderVisits.tickScheduledVisit(level, levelData, failedSite);
            helper.assertTrue(attempts[0] == 1 && schedule.nextAttemptGameTime() == now + 1200,
                    "Empty inventory must admit due visit; failed placement must retry in one minute");
            var loaded = WanderingTraderScheduleData.load(schedule.save(new CompoundTag()));
            storage.set("better_spirit_commerce_wandering_trader", loaded);
            WanderingTraderVisits.tickScheduledVisit(level, levelData, failedSite);
            helper.assertTrue(attempts[0] == 1, "Reload lost retry deadline");
            loaded.scheduleRetry(now, 0);
            WanderingTraderVisits.tickScheduledVisit(level, levelData, failedSite);
            helper.assertTrue(attempts[0] == 2 && loaded.nextAttemptGameTime() == now + 1200,
                    "Pending placement did not retry, or rerolled multi-day interval");
        } finally {
            level.players().remove(player);
            storage.set("better_spirit_commerce_wandering_trader", oldSchedule);
            storage.set("better_spirit_commerce_trader_camp", oldCamp);
            rules.getRule(net.minecraft.world.level.GameRules.RULE_DO_TRADER_SPAWNING).set(traderRule, level.getServer());
            rules.getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(mobRule, level.getServer());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void nearestLivingNonSpectatorSuppliesThePersonalMarketHistory(GameTestHelper helper) {
        var level = helper.getLevel();
        var near = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "market-near"));
        var far = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "market-far"));
        var spectator = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "market-spectator"));
        near.setGameMode(GameType.SURVIVAL);
        far.setGameMode(GameType.SURVIVAL);
        near.getInventory().clearContent();
        far.getInventory().clearContent();
        near.getInventory().setItem(0, new ItemStack(CurrencyItems.item(CurrencyIdentity.WORK).get(), 10));
        far.getInventory().setItem(0, new ItemStack(CurrencyItems.item(CurrencyIdentity.WORK).get(), 10));
        spectator.setGameMode(GameType.SPECTATOR);
        var trader = EntityType.WANDERING_TRADER.create(level);
        helper.assertTrue(trader != null, "Native trader creation failed");
        trader.moveTo(0, 80, 0);
        near.moveTo(2, 80, 0);
        far.moveTo(20, 80, 0);
        spectator.moveTo(0, 80, 0);
        helper.assertTrue(WanderingTraderVisits.collectionAnchor(List.of(far, spectator, near), trader) == near,
                "Another player's collection or a closer spectator must not replace the local player's history");
        near.setHealth(0);
        helper.assertTrue(WanderingTraderVisits.collectionAnchor(List.of(near, far, spectator), trader) == far,
                "Dead players cannot anchor the collection-driven market");
        helper.assertTrue(WanderingTraderVisits.collectionAnchor(List.of(spectator), trader) == null,
                "No eligible player must use the random fallback");
        helper.succeed();
    }
}

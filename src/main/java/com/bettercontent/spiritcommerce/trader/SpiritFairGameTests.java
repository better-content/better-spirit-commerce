package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class SpiritFairGameTests {
    private SpiritFairGameTests() { }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void additionalVisitAdmissionRelocationStockAndDawn(final GameTestHelper helper) {
        var level = helper.getLevel();
        var storage = level.getDataStorage();
        var originalFair = SpiritFairData.get(level);
        var originalCamp = TraderCampData.fair(level);
        var originalOrdinaryCamp = TraderCampData.get(level);
        var ordinary = WanderingTraderScheduleData.get(level);
        CompoundTag ordinaryBefore = ordinary.save(new CompoundTag());
        long originalTime = level.getDayTime();
        boolean traderRule = level.getGameRules().getBoolean(GameRules.RULE_DO_TRADER_SPAWNING);
        boolean mobRule = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        WanderingTrader fair = null;
        WanderingTrader regular = null;
        CancelInsertion insertion = new CancelInsertion();
        try {
            storage.set("better_spirit_commerce_spirit_fair", new SpiritFairData());
            storage.set("better_spirit_commerce_trader_camp_spirit_fair", new TraderCampData());
            storage.set("better_spirit_commerce_trader_camp", new TraderCampData());
            level.setDayTime(13000);
            level.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(true, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, level.getServer());
            // A bounded native fixture away from the small shared test template.
            BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 16));
            // Spawn-region terrain can be sea-level water above GameTest's deep template Y.
            // Build the declared supported pad above that terrain, not buried underneath it.
            BlockPos playerPos = new BlockPos(anchor.getX(), level.getMaxBuildHeight() - 16, anchor.getZ());
            BlockPos site = playerPos.north(16);
            helper.assertTrue(level.hasChunksAt(site.offset(-16, -2, -16), site.offset(16, 4, 16)),
                    "Fixture must use the already-loaded spawn region, not generate terrain on the test tick");
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                BlockPos floor = site.offset(x, -1, z);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(floor.above(y + 1), Blocks.AIR.defaultBlockState());
            }
            var first = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "fair-first"));
            var second = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "fair-second"));
            first.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            second.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            first.moveTo(playerPos, 0, 0);
            second.moveTo(playerPos, 0, 0);
            var players = List.<net.minecraft.server.level.ServerPlayer>of(first, second);
            var window = new SpiritFairVisits.Window(1, 0);
            if (net.minecraftforge.fml.ModList.get().isLoaded("better_celestial_events")) {
                level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                        "celestial force spirit_fair");
                window = SpiritFairBridge.snapshot(level.getServer());
                helper.assertTrue(window != null, "Actual forced provider snapshot did not bridge");
            }
            helper.assertTrue(!SpiritFairVisits.tick(level, window, List.of()), "No-player night admitted a visit");
            helper.assertTrue(!SpiritFairData.get(level).canAttempt(window.occurrence(), level.getGameTime() + 1199),
                    "Failed search was not throttled for a minute");
            storage.set("better_spirit_commerce_spirit_fair", new SpiritFairData());
            level.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, level.getServer());
            helper.assertTrue(!SpiritFairVisits.tick(level, window, players), "Trader gamerule ignored");
            storage.set("better_spirit_commerce_spirit_fair", new SpiritFairData());
            level.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(true, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            helper.assertTrue(!SpiritFairVisits.tick(level, window, players), "Mob gamerule ignored");
            storage.set("better_spirit_commerce_spirit_fair", new SpiritFairData());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, level.getServer());
            level.setBlockAndUpdate(site.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
            helper.assertTrue(!SpiritFairVisits.tick(level, window, players),
                    "Hazardous support or distant-below-player terrain admitted a camp");
            storage.set("better_spirit_commerce_spirit_fair", new SpiritFairData());
            level.setBlockAndUpdate(site.below(), Blocks.STONE.defaultBlockState());
            first.moveTo(playerPos.offset(10000, 0, 10000), 0, 0);
            helper.assertTrue(!SpiritFairVisits.tick(level, window, List.of(first)), "Unloaded terrain admitted a camp");
            storage.set("better_spirit_commerce_spirit_fair", new SpiritFairData());
            first.moveTo(playerPos, 0, 0);
            regular = EntityType.WANDERING_TRADER.create(level);
            regular.moveTo(site.east(5), 0, 0);
            level.addFreshEntity(regular);
            ordinary.completeVisit(level.getGameTime(), 120000, regular.getUUID());
            UUID regularId = regular.getUUID();
            helper.assertTrue(TraderCampService.createForVisit(regular, WanderingTraderTheme.AQUEOUS),
                    "Ordinary camp fixture failed");
            BlockPos ordinaryPost = TraderCampData.forTrader(regular).targetPos();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(insertion);
            try {
                helper.assertTrue(!SpiritFairVisits.tick(level, window, players), "Canceled insertion reported success");
            } finally {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(insertion);
            }
            helper.assertTrue(SpiritFairData.get(level).successfulTrader() == null
                            && SpiritFairData.get(level).nextTheme() == WanderingTraderTheme.SACRED
                            && !SpiritFairData.get(level).canAttempt(window.occurrence(), level.getGameTime() + 1199)
                            && SpiritFairData.get(level).canAttempt(window.occurrence(), level.getGameTime() + 1200)
                            && TraderCampData.fairTraderIds(level).isEmpty(),
                    "Canceled native insertion consumed admission/theme, lost throttle, or left a camp");
            expireRetryForFixture(level);
            insertion.cancel = false;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(insertion);
            boolean spawned = SpiritFairVisits.tick(level, window, players);
            BlockPos searchHeight = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site);
            helper.assertTrue(spawned, "Loaded safe camp did not spawn: site=" + site + " height=" + searchHeight
                    + " loaded=" + level.hasChunkAt(site)
                    + " area=" + level.hasChunksAt(searchHeight.offset(-8, -2, -8), searchHeight.offset(8, 4, 8))
                    + " alive=" + first.isAlive() + " spectator=" + first.isSpectator()
                    + " support=" + level.getBlockState(searchHeight.below())
                    + " fair=" + SpiritFairData.get(level).save(new CompoundTag()));
            UUID fairId = SpiritFairData.get(level).successfulTrader();
            // Native chunk entity visibility may publish after insertion; retain the actual Forge join
            // instance and verify world admission + saved UUID instead of an immediate visibility lookup.
            fair = insertion.lastJoined;
            helper.assertTrue(fair != null && fair.isAddedToWorld() && fairId.equals(fair.getUUID())
                            && !regularId.equals(fairId) && regularId.equals(ordinary.activeTraderId()),
                    "Fair must coexist without overwriting ordinary trader");
            helper.assertTrue(!SpiritFairVisits.tick(level, window, players), "Two players created a second admission");
            var offers = fair.getOffers();
            var offer = offers.get(0);
            offer.increaseUses();
            CompoundTag entitySaved = new CompoundTag();
            fair.save(entitySaved);
            SpiritFairData restarted = SpiritFairData.load(SpiritFairData.get(level).save(new CompoundTag()));
            storage.set("better_spirit_commerce_spirit_fair", restarted);
            helper.assertTrue(!SpiritFairVisits.tick(level, window, players), "Restart replayed an occurrence");

            BlockPos old = BlockPos.of(fair.getPersistentData().getLong(TraderCampService.CAMP_POS_TAG));
            var post = (TraderCampPostBlockEntity) level.getBlockEntity(old);
            CompoundTag item = post.saveForItem();
            helper.assertTrue(item.getLong("FairOccurrence") == window.occurrence(), "Camp lost occurrence identity");
            BlockPos moved = old.east(3);
            level.setBlockAndUpdate(old, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(moved, TraderCampRegistries.POST.get().defaultBlockState()
                    .setValue(TraderCampPostBlock.FACING, Direction.EAST));
            var movedPost = (TraderCampPostBlockEntity) level.getBlockEntity(moved);
            movedPost.load(item);
            movedPost.retargetLinkedTrader();
            helper.assertTrue(offer.getUses() == 1 && TraderCampData.forTrader(fair).targetPos().equals(moved),
                    "Relocation reset stock or lost independent camp target");
            UUID otherOccurrenceTrader = UUID.randomUUID();
            TraderCampData.fair(level, otherOccurrenceTrader).setTarget(otherOccurrenceTrader,
                    level.dimension().location().toString(), moved.west(8), Direction.NORTH);
            storage.set("better_spirit_commerce_trader_camp_spirit_fair",
                    TraderCampData.load(TraderCampData.fair(level).save(new CompoundTag())));
            helper.assertTrue(TraderCampData.forTrader(fair).targetPos().equals(moved)
                            && TraderCampData.fair(level, otherOccurrenceTrader).targetPos().equals(moved.west(8)),
                    "Saved Fair occurrences overwrote each other's relocated camps");
            TraderCampData.fair(level, otherOccurrenceTrader).clear(otherOccurrenceTrader);
            // Native entity NBT retains used offers, tags and UUID through unload/rejoin.
            WanderingTrader reloaded = EntityType.WANDERING_TRADER.create(level);
            reloaded.load(entitySaved);
            helper.assertTrue(reloaded.getUUID().equals(fairId) && reloaded.getOffers().get(0).getUses() == 1,
                    "Reload changed identity or refreshed stock");
            TraderCampService.restoreOnJoin(reloaded);
            helper.assertTrue(reloaded.getPersistentData().getLong(TraderCampService.CAMP_POS_TAG) == moved.asLong(),
                    "Reload did not restore relocated target");
            for (var entry : fair.getOffers()) while (!entry.isOutOfStock()) entry.increaseUses();
            helper.assertTrue(!SpiritFairVisits.maintain(fair) && fair.isRemoved(), "Sold-out fair stayed");
            helper.assertTrue(!SpiritFairVisits.tick(level, window, players), "Sold-out fair was replaced");
            WanderingTrader dead = EntityType.WANDERING_TRADER.create(level);
            dead.load(entitySaved);
            dead.hurt(level.damageSources().generic(), Float.MAX_VALUE);
            helper.assertTrue(dead.isRemoved() && !SpiritFairVisits.tick(level, window, players),
                    "Death granted replacement or left Fair alive");
            level.setDayTime(24000);
            helper.assertTrue(!level.addFreshEntity(reloaded) && reloaded.isRemoved(),
                    "Unloaded trader returned after dawn instead of expiring on native join");
            helper.assertTrue(regularId.equals(ordinary.activeTraderId()) && regular.isAlive()
                            && TraderCampData.forTrader(regular).targetPos().equals(ordinaryPost)
                            && level.getBlockEntity(ordinaryPost) instanceof TraderCampPostBlockEntity,
                    "Fair departure affected ordinary trader");
            helper.assertTrue(!SpiritFairVisits.tick(level, window, players), "Dawn spawned expired occurrence");
            if (net.minecraftforge.fml.ModList.get().isLoaded("better_celestial_events")) {
                level.setDayTime(13000);
                var forced = forceWindow(helper);
                expireRetryForFixture(level);
                helper.assertTrue(SpiritFairVisits.tick(level, forced, players), "New forced occurrence could not visit");
                fair = insertion.lastJoined;
                helper.assertTrue(fair.isAddedToWorld() && fair.getUUID().equals(SpiritFairData.get(level).successfulTrader()),
                        "Forced occurrence did not expose its successfully inserted native trader");
                BlockPos forcedPost = TraderCampData.forTrader(fair).targetPos();
                var replacement = forceWindow(helper);
                helper.assertTrue(replacement.occurrence() != forced.occurrence()
                                && !SpiritFairVisits.maintain(fair) && fair.isRemoved()
                                && level.getBlockState(forcedPost).isAir(),
                        "Same-night replacement left the previous occurrence's visitor/camp active");
                expireRetryForFixture(level);
                helper.assertTrue(SpiritFairVisits.tick(level, replacement, players), "Replacement occurrence did not visit");
                fair = insertion.lastJoined;
                helper.assertTrue(fair.isAddedToWorld() && fair.getUUID().equals(SpiritFairData.get(level).successfulTrader()),
                        "Replacement occurrence did not expose its successfully inserted native trader");
                level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "celestial end");
                helper.assertTrue(!SpiritFairVisits.maintain(fair) && fair.isRemoved()
                                && !SpiritFairVisits.tick(level, replacement, players),
                        "Forced end left a live visitor or allowed replacement/restock");
            }
            helper.succeed();
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(insertion);
            if (fair != null && !fair.isRemoved()) TraderCampService.depart(fair, level);
            if (regular != null) TraderCampService.depart(regular, level);
            storage.set("better_spirit_commerce_spirit_fair", originalFair);
            storage.set("better_spirit_commerce_trader_camp_spirit_fair", originalCamp);
            storage.set("better_spirit_commerce_trader_camp", originalOrdinaryCamp);
            storage.set("better_spirit_commerce_wandering_trader", WanderingTraderScheduleData.load(ordinaryBefore));
            if (net.minecraftforge.fml.ModList.get().isLoaded("better_celestial_events")) {
                level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "celestial end");
            }
            level.setDayTime(originalTime);
            level.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(traderRule, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(mobRule, level.getServer());
        }
    }

    private static void expireRetryForFixture(final net.minecraft.server.level.ServerLevel level) {
        // Model passage of the already-asserted retry deadline without a 60-second wall-clock test.
        // Preserve the occurrence high-water mark, successful UUID and theme cursor through native NBT.
        CompoundTag elapsed = SpiritFairData.get(level).save(new CompoundTag());
        elapsed.putLong("NextAttempt", 0);
        level.getDataStorage().set("better_spirit_commerce_spirit_fair", SpiritFairData.load(elapsed));
    }

    private static SpiritFairVisits.Window forceWindow(final GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "celestial force spirit_fair");
        var window = SpiritFairBridge.snapshot(server);
        helper.assertTrue(window != null, "Native force command did not publish a Fair");
        return window;
    }

    public static final class CancelInsertion {
        boolean cancel = true;
        WanderingTrader lastJoined;

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public void cancel(final net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof WanderingTrader trader && SpiritFairVisits.isFair(trader)) {
                lastJoined = trader;
                if (cancel) event.setCanceled(true);
            }
        }
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void optionalBridgeIsInertWithoutProvider(final GameTestHelper helper) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("better_celestial_events")) {
            helper.assertTrue(SpiritFairBridge.snapshot(helper.getLevel().getServer()) == null,
                    "Absent provider must be inert without linkage errors");
        } else {
            var server = helper.getLevel().getServer();
            long originalTime = server.overworld().getDayTime();
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "celestial force open_ways");
                helper.assertTrue(SpiritFairBridge.snapshot(server) == null, "Another celestial event admitted a Fair");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "celestial force spirit_fair");
                var active = SpiritFairBridge.snapshot(server);
                helper.assertTrue(active != null, "Active native provider snapshot was not bridged");
                server.overworld().setDayTime((active.scheduledDay() + 1) * 24000L);
                helper.assertTrue(SpiritFairBridge.snapshot(server) == null, "Stale provider snapshot survived dawn");
            } finally {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "celestial end");
                server.overworld().setDayTime(originalTime);
            }
        }
        helper.succeed();
    }
}

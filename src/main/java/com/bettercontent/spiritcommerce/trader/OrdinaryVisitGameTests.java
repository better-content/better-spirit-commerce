package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;

/** Real native spawner/mixin integration, not a replacement SpawnAttempt. */
@GameTestHolder("better_spirit_commerce")
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class OrdinaryVisitGameTests {
    @GameTest(templateNamespace = "better_spirit_commerce", template = "empty", timeoutTicks = 200)
    public static void nativeSpawnRollbackLocalAdmissionAndSavedDeparture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var storage = level.getServer().overworld().getDataStorage();
        var original = TraderCampData.get(level);
        var data = (ServerLevelData) level.getLevelData();
        UUID previous = data.getWanderingTraderId();
        int oldDelay = data.getWanderingTraderSpawnDelay(), oldChance = data.getWanderingTraderSpawnChance();
        boolean oldTrader = level.getGameRules().getBoolean(GameRules.RULE_DO_TRADER_SPAWNING);
        boolean oldMob = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "native-market"));
        var near = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "native-market-near"));
        var far = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "native-market-far"));
        var observer = new Insertions();
        BlockPos site = helper.absolutePos(new BlockPos(4, 3, 4));
        site = new BlockPos(site.getX(), level.getMaxBuildHeight() - 18, site.getZ());
        List<WanderingTrader> admitted = new ArrayList<>();
        TraderLlama playerOwned = net.minecraft.world.entity.EntityType.TRADER_LLAMA.create(level);
        try {
            storage.set("better_spirit_commerce_trader_camp", new TraderCampData());
            level.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(true, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, level.getServer());
            helper.assertTrue(level.hasChunksAt(site.offset(-22, -1, -22), site.offset(22, 12, 22)), "Native fixture pad must be loaded");
            for (int x = -22; x <= 22; x++) for (int z = -22; z <= 22; z++) {
                level.setBlockAndUpdate(site.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
            for (var anchor : List.of(player, near, far)) {
                anchor.setGameMode(GameType.SURVIVAL);
                anchor.getInventory().clearContent();
                anchor.getInventory().setItem(0, new ItemStack(CurrencyItems.item(CurrencyIdentity.WORK).get(), 10));
                anchor.moveTo(site.getX(), site.getY(), site.getZ());
            }
            far.moveTo(site.getX() + 10000, site.getY(), site.getZ());
            level.players().add(player);
            playerOwned.moveTo(site.getX(), site.getY(), site.getZ());
            playerOwned.setTamed(true);
            playerOwned.setOwnerUUID(player.getUUID());
            level.addFreshEntity(playerOwned);
            MinecraftForge.EVENT_BUS.register(observer);
            WanderingTraderSpawner spawner = new WanderingTraderSpawner(data);
            observer.breakCamp = true;
            // Bounded terrain search retries are immediate, never game-time gates.
            for (int attempt = 0; attempt < 128 && observer.llamas.isEmpty(); attempt++) spawner.tick(level, true, true);
            helper.assertTrue(!observer.traders.isEmpty() && !observer.llamas.isEmpty(), "Actual native trader and companion insertion not exercised");
            helper.assertTrue(observer.traders.stream().allMatch(Entity::isRemoved)
                    && observer.llamas.stream().allMatch(Entity::isRemoved), "Failed camp left native trader/own llama orphaned");
            helper.assertTrue(java.util.Objects.equals(previous, data.getWanderingTraderId()), "Rollback did not restore native UUID");
            helper.assertTrue(TraderCampData.ordinaryTraderIds(level).isEmpty(), "Failed camp retained admission");
            helper.assertTrue(playerOwned.isAlive() && !playerOwned.isRemoved(), "Rollback removed an unrelated player-owned llama");
            observer.protectOwner = player.getUUID();
            int llamaCount = observer.llamas.size();
            for (int attempt = 0; attempt < 128 && observer.llamas.size() == llamaCount; attempt++) spawner.tick(level, true, true);
            helper.assertTrue(observer.llamas.size() > llamaCount, "Native owned-companion negative not exercised");
            helper.assertTrue(observer.llamas.subList(llamaCount, observer.llamas.size()).stream()
                    .allMatch(llama -> !llama.isRemoved() && player.getUUID().equals(llama.getOwnerUUID())),
                    "Rollback deleted newly player-owned companions");
            observer.protectOwner = null;
            observer.breakCamp = false;
            WanderingTrader trader = spawn(helper, level, data, spawner);
            admitted.add(trader);
            TraderCampData camp = TraderCampData.forTrader(trader);
            long expiry = camp.expiresAt();
            helper.assertTrue(expiry == level.getGameTime() + 12000 && player.getUUID().equals(camp.anchorOwner()), "Immutable lifetime/owner missing");
            helper.assertTrue(spawner.tick(level, true, true) == 0 && !WanderingTraderVisits.eligibleAnchor(level, near), "Local duplicate admitted");
            helper.assertTrue(WanderingTraderVisits.eligibleAnchor(level, far), "Distant player's arrival globally blocked");
            // A second real native visit in a separately authored, explicitly loaded region.
            BlockPos remote = site.east(640);
            for (int cx = (remote.getX() - 28) >> 4; cx <= (remote.getX() + 28) >> 4; cx++)
                for (int cz = (remote.getZ() - 28) >> 4; cz <= (remote.getZ() + 28) >> 4; cz++) level.getChunk(cx, cz);
            helper.assertTrue(level.hasChunksAt(remote.offset(-28, 0, -28), remote.offset(28, 0, 28)), "Remote fixture chunks not loaded");
            for (int x = -22; x <= 22; x++) for (int z = -22; z <= 22; z++)
                level.setBlockAndUpdate(remote.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            far.moveTo(remote.getX(), remote.getY(), remote.getZ());
            level.players().add(near);
            level.players().add(far);
            WanderingTrader remoteTrader = spawn(helper, level, data, spawner);
            admitted.add(remoteTrader);
            helper.assertTrue(TraderCampData.forTrader(remoteTrader).anchorOwner().equals(far.getUUID())
                    && trader.isAlive(), "Second regional native visit displaced or reused first visit");
            level.players().remove(near);
            level.players().remove(far);
            TraderCampService.depart(remoteTrader, level);
            // Restart the actual SavedData through its NBT API while the entity remains native.
            storage.set("better_spirit_commerce_trader_camp", TraderCampData.load(TraderCampData.get(level).save(new CompoundTag())));
            helper.assertTrue(TraderCampData.forTrader(trader).expiresAt() == expiry
                    && !WanderingTraderVisits.eligibleAnchor(level, player), "Restart lost local occupancy/deadline");
            TraderCampService.restoreOnJoin(trader);
            helper.assertTrue(TraderCampData.forTrader(trader).expiresAt() == expiry, "Reload renewed visit lifetime");
            // Simulate an entity absent from the loaded entity index, retaining its saved camp.
            CompoundTag savedTrader = new CompoundTag();
            trader.save(savedTrader);
            trader.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            TraderCampData.forTrader(trader).begin(player.getUUID(), player.blockPosition(), level.getGameTime());
            // Saved unloaded scenery at a location that must not be force-loaded for retirement.
            BlockPos unloaded = site.east(10000);
            helper.assertTrue(!level.hasChunkAt(unloaded), "Unloaded expiry fixture unexpectedly loaded");
            BlockPos oldPost = TraderCampData.forTrader(trader).targetPos();
            TraderCampData.forTrader(trader).setTarget(trader.getUUID(), level.dimension().location().toString(), unloaded, net.minecraft.core.Direction.NORTH);
            TraderCampService.expireOrdinaryVisits(level);
            helper.assertTrue(!level.hasChunkAt(unloaded), "Expiry force-loaded scenery");
            if (level.getBlockEntity(oldPost) instanceof TraderCampPostBlockEntity post) post.onLoad();
            helper.assertTrue(level.isEmptyBlock(oldPost), "Expired post survived saved-owner validation");
            // Native entity NBT returning after expiration must not revive its stock/camp.
            savedTrader.getCompound("ForgeData").putLong(WanderingTraderVisits.EXPIRY_TAG, level.getGameTime());
            Entity reloaded = net.minecraft.world.entity.EntityType.loadEntityRecursive(savedTrader, level, entity -> entity);
            helper.assertTrue(reloaded instanceof WanderingTrader, "Native trader NBT failed to reload");
            level.addFreshEntity(reloaded);
            helper.assertTrue(reloaded.isRemoved(), "Expired unloaded trader rejoined after restart");
            helper.assertTrue(WanderingTraderVisits.eligibleAnchor(level, player), "Expired absent trader blocked travel");
            WanderingTrader next = spawn(helper, level, data, spawner);
            admitted.add(next);
            TraderCampService.depart(next, level);
            helper.assertTrue(WanderingTraderVisits.eligibleAnchor(level, player), "Departure imposed an arrival wait");
            helper.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(observer);
            for (WanderingTrader trader : admitted) if (!trader.isRemoved()) TraderCampService.depart(trader, level);
            if (playerOwned != null) playerOwned.discard();
            observer.llamas.forEach(Entity::discard);
            observer.traders.forEach(Entity::discard);
            level.players().remove(player);
            level.players().remove(near);
            level.players().remove(far);
            storage.set("better_spirit_commerce_trader_camp", original);
            data.setWanderingTraderId(previous);
            data.setWanderingTraderSpawnDelay(oldDelay);
            data.setWanderingTraderSpawnChance(oldChance);
            level.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(oldTrader, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(oldMob, level.getServer());
        }
    }

    private static WanderingTrader spawn(GameTestHelper helper, ServerLevel level, ServerLevelData data,
                                         WanderingTraderSpawner spawner) {
        for (int attempt = 0; attempt < 128; attempt++) {
            if (spawner.tick(level, true, true) == 1) return (WanderingTrader) level.getEntity(data.getWanderingTraderId());
        }
        helper.fail("Native loaded-site admission failed across 128 immediate attempts");
        throw new IllegalStateException();
    }

    public static final class Insertions {
        boolean breakCamp;
        UUID protectOwner;
        final List<WanderingTrader> traders = new ArrayList<>();
        final List<TraderLlama> llamas = new ArrayList<>();
        @SubscribeEvent public void joined(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof WanderingTrader trader) {
                traders.add(trader);
                if (breakCamp) trader.setPos(trader.getX(), trader.getY() + 10, trader.getZ());
            }
            if (event.getEntity() instanceof TraderLlama llama) {
                llamas.add(llama);
                if (protectOwner != null) llama.setOwnerUUID(protectOwner);
            }
        }
    }
}

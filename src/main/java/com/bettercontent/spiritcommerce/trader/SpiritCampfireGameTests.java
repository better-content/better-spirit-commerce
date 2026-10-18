package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.config.EconomyPolicy;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("better_spirit_commerce")
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class SpiritCampfireGameTests {
    private SpiritCampfireGameTests() { }
    @GameTest(templateNamespace = "better_spirit_commerce", template = "empty", timeoutTicks = 100)
    public static void palettesOffersOwnershipAndRecovery(GameTestHelper helper) {
        var level = helper.getLevel();
        var storage = level.getServer().overworld().getDataStorage();
        CompoundTag originalCamp = TraderCampData.get(level).save(new CompoundTag());
        CompoundTag originalSchedule = WanderingTraderScheduleData.get(level).save(new CompoundTag());
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 3, 4));
        BlockPos site = new BlockPos(anchor.getX(), level.getMaxBuildHeight() - 32, anchor.getZ());
        try {
            storage.set("better_spirit_commerce_trader_camp", new TraderCampData());
            helper.assertTrue(level.hasChunksAt(site.offset(-7, -1, -7), site.offset(7, 4, 7)), "Fixture terrain must already be loaded");
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                BlockPos floor = site.offset(x, -1, z);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                for (int y = 0; y < 4; y++) level.setBlockAndUpdate(floor.above(y + 1), Blocks.AIR.defaultBlockState());
            }
            trader.moveTo(site.getX() + .5, site.getY(), site.getZ() + .5, 0, 0);
            level.addFreshEntity(trader);
            WanderingTraderScheduleData.get(level).completeVisit(level.getGameTime(), 120000, trader.getUUID());
            helper.assertTrue(TraderCampService.createForVisit(trader, WanderingTraderTheme.AQUEOUS), "Camp failed");
            helper.assertTrue(TraderCampService.moveToCamp(trader), "Settlement failed");
            TraderCampFireService.maintain(trader);
            TraderCampData camp = TraderCampData.forTrader(trader);
            BlockPos firePos = camp.firePos();
            helper.assertTrue(firePos != null && level.getBlockState(firePos).is(SpiritCampfires.block(SpiritKind.AQUEOUS)), "Wrong/missing themed fire");
            var fire = (SpiritCampfireBlockEntity) level.getBlockEntity(firePos);
            helper.assertTrue(fire.ownedBy(trader.getUUID()) && fire.validCamp(level), "Ownership missing");
            level.setBlockAndUpdate(firePos, fire.getBlockState().setValue(SpiritCampfireBlock.LIT, false));
            TraderCampService.restoreOnJoin(trader);
            helper.assertTrue(camp.firePos().equals(firePos) && level.getBlockEntity(firePos) == fire
                    && !fire.getBlockState().getValue(SpiritCampfireBlock.LIT), "Unchanged reload reset the campfire");
            CompoundTag saved = fire.saveWithFullMetadata();
            helper.assertTrue(saved.getString("id").equals("better_spirit_commerce:spirit_campfire"), "Wrong persisted BE type");
            BlockEntity restored = BlockEntity.loadStatic(firePos, fire.getBlockState(), saved);
            helper.assertTrue(restored instanceof SpiritCampfireBlockEntity reloaded && reloaded.ownedBy(trader.getUUID()), "Ownership did not survive reload");
            CompoundTag expired = saved.copy();
            expired.putBoolean("SpiritFair", true);
            expired.putLong("FairStart", 0);
            expired.putLong("FairEnd", level.getServer().overworld().getDayTime());
            var expiredFire = (SpiritCampfireBlockEntity) BlockEntity.loadStatic(firePos, fire.getBlockState(), expired);
            helper.assertTrue(!expiredFire.validCamp(level), "Expired Fair fire needs no loaded trader/post to retire");
            var loot = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, firePos.getCenter())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, net.minecraft.world.item.ItemStack.EMPTY)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY, fire);
            helper.assertTrue(fire.getBlockState().getDrops(loot).isEmpty(), "Camp scenery farmed a free item");
            BlockPos boughtPos = site.east(6);
            level.setBlockAndUpdate(boughtPos, SpiritCampfires.block(SpiritKind.AQUEOUS).defaultBlockState());
            var bought = (SpiritCampfireBlockEntity) level.getBlockEntity(boughtPos);
            helper.assertTrue(!bought.isCampOwned() && bought.validCamp(level), "Purchased fire incorrectly owned");
            var boughtLoot = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, boughtPos.getCenter())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, net.minecraft.world.item.ItemStack.EMPTY)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY, bought);
            helper.assertTrue(bought.getBlockState().getDrops(boughtLoot).stream().anyMatch(s -> s.is(SpiritCampfires.item(SpiritKind.AQUEOUS))), "Purchased fire not recoverable");
            camp.setFirePos(null); // Simulate a retarget while the old fire is unloaded.
            helper.assertTrue(!fire.validCamp(level), "Obsolete unloaded fire would remain orphaned");
            camp.setFirePos(firePos);
            level.setBlockAndUpdate(firePos, Blocks.DIAMOND_BLOCK.defaultBlockState());
            TraderCampFireService.remove(level, camp, trader.getUUID());
            helper.assertTrue(level.getBlockState(firePos).is(Blocks.DIAMOND_BLOCK), "Cleanup removed player's replacement");
            for (SpiritKind kind : SpiritKind.values()) {
                trader.getPersistentData().putString(WanderingTraderVisits.THEME_TAG, kind.id());
                var offers = trader.getOffers(); // Native mixin also adds the required profession-egg offer.
                var matching = offers.stream().filter(o -> o.getResult().is(SpiritCampfires.item(kind))).toList();
                helper.assertTrue(matching.size() == 1 && matching.get(0).getBaseCostA().getCount() == 1
                        && matching.get(0).getBaseCostA().is(CurrencyItems.item(kind.currencyIdentity()).get()), "Invalid one-spirit themed offer: " + kind);
                matching.get(0).increaseUses();
                trader.getOffers();
                helper.assertTrue(matching.get(0).getUses() == 1 && offers.contains(matching.get(0)), "Catalogue reset fire stock");
                helper.assertTrue(EconomyPolicy.wanderingRows(kind).size() == 36, "Missing authored row");
            }
            TraderCampService.depart(trader, level);
            helper.assertTrue(level.getBlockState(boughtPos).is(SpiritCampfires.block(SpiritKind.AQUEOUS)), "Departure removed purchased fire");
            helper.succeed();
        } finally {
            if (trader != null && !trader.isRemoved()) TraderCampService.depart(trader, level);
            storage.set("better_spirit_commerce_trader_camp", TraderCampData.load(originalCamp));
            storage.set("better_spirit_commerce_wandering_trader", WanderingTraderScheduleData.load(originalSchedule));
        }
    }
}

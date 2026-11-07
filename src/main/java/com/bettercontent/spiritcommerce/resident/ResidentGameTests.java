package com.bettercontent.spiritcommerce.resident;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class ResidentGameTests {
    private ResidentGameTests() {}

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void workshopBedProvidesHomePoi(GameTestHelper helper) {
        BlockPos foot = new BlockPos(2, 2, 1);
        BlockPos head = foot.south();
        helper.setBlock(foot.below(), Blocks.STONE);
        helper.setBlock(head.below(), Blocks.STONE);
        SettlementGeneration.placeWorkshopBed(helper.getLevel(), helper.absolutePos(foot));
        helper.assertTrue(helper.getBlockState(foot).getValue(BedBlock.PART) == BedPart.FOOT
                        && helper.getBlockState(head).getValue(BedBlock.PART) == BedPart.HEAD
                        && helper.getLevel().getPoiManager().existsAtPosition(PoiTypes.HOME, helper.absolutePos(head))
                        && !helper.getLevel().getPoiManager().existsAtPosition(PoiTypes.HOME, helper.absolutePos(foot)),
                "Workshop bed must have a matching head with a native HOME POI");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void stockedBarterConservesGoods(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        ResidentState state = fresh(villager);
        state.add(new ItemStack(Items.CARROT, 5));
        ItemStack wanted = new ItemStack(Items.CARROT);
        ItemStack payment = new ItemStack(Items.OAK_LOG, 2);
        AtomicReference<ItemStack> received = new AtomicReference<>(ItemStack.EMPTY);
        boolean done = ResidentBarter.execute(helper.getLevel(), villager, wanted, payment,
                () -> true, received::set);
        ResidentState after = ResidentState.of(villager);
        helper.assertTrue(done && received.get().is(Items.CARROT) && received.get().getCount() == 1
                        && after.count(wanted) == 4 && after.count(payment) == 2,
                "Stock barter must consume one offered carrot and retain both payment logs");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void recipeChainRunsAtTradeTime(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.CRAFTING_TABLE);
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        ResidentState state = fresh(villager);
        state.setNeeds(5, 18, 18);
        state.add(new ItemStack(Items.OAK_LOG, 3));
        ItemStack wanted = new ItemStack(Items.STICK, 4);
        ItemStack payment = new ItemStack(Items.CARROT, 4);
        ResidentBarter.Quote quote = ResidentBarter.quote(helper.getLevel(), villager, wanted, payment);
        helper.assertTrue(quote.available() && quote.recipe() != null,
                "Log to planks to sticks must be quoted from one crafting station: " + quote.blocker());
        AtomicReference<ItemStack> received = new AtomicReference<>(ItemStack.EMPTY);
        helper.assertTrue(ResidentBarter.execute(helper.getLevel(), villager, wanted, payment,
                () -> true, received::set), "The quoted chain must commit");
        ResidentState after = ResidentState.of(villager);
        helper.assertTrue(received.get().is(Items.STICK) && received.get().getCount() == 4
                        && after.count(new ItemStack(Items.OAK_LOG)) == 2
                        && after.count(new ItemStack(Items.OAK_PLANKS)) == 2
                        && after.count(payment) == 4,
                "Lazy craft must consume one log, return four sticks, and retain the two spare planks");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void blockedCraftDoesNotTakePayment(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        ResidentState state = fresh(villager);
        state.setNeeds(5, 18, 18);
        state.add(new ItemStack(Items.OAK_LOG, 3));
        ItemStack wanted = new ItemStack(Items.STICK, 4);
        ItemStack payment = new ItemStack(Items.CARROT, 4);
        int[] callbacks = {0};
        helper.assertTrue(!ResidentBarter.quote(helper.getLevel(), villager, wanted, payment).available(),
                "Crafting without a station must be blocked");
        helper.assertTrue(!ResidentBarter.execute(helper.getLevel(), villager, wanted, payment,
                () -> { callbacks[0]++; return true; }, ignored -> callbacks[0]++),
                "A blocked quote cannot commit");
        helper.assertTrue(callbacks[0] == 0 && ResidentState.of(villager).count(new ItemStack(Items.OAK_LOG)) == 3,
                "A blocked transaction must not take payment or mutate resident stock");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void residentsExchangeComplementarySupplies(GameTestHelper helper) {
        Villager grower = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
        ResidentRules.setWorksite(grower, grower.blockPosition());
        ResidentRules.setWorksite(carrier, grower.blockPosition());
        ResidentState first = fresh(grower);
        ResidentState second = fresh(carrier);
        first.setNeeds(18, 5, 18);
        second.setNeeds(5, 18, 18);
        first.add(new ItemStack(Items.CARROT, 10));
        second.add(ResidentRules.waterBottle(3).copyWithCount(6));
        helper.assertTrue(ResidentRules.needs(ResidentRules.waterBottle(3), first)
                        && ResidentRules.needs(new ItemStack(Items.CARROT), second),
                "Fixture must have complementary needs");
        helper.assertTrue(ResidentBarter.offers(helper.getLevel(), grower).stream()
                        .anyMatch(o -> o.result().is(Items.CARROT))
                        && ResidentBarter.offers(helper.getLevel(), carrier).stream()
                        .anyMatch(o -> ResidentRules.isSafeWater(o.result())),
                "Both fixture residents must advertise their stock");
        helper.assertTrue(ResidentExchange.settle(helper.getLevel(), grower),
                "Residents with complementary needs must settle one barter cycle");
        ResidentState afterFirst = ResidentState.of(grower);
        ResidentState afterSecond = ResidentState.of(carrier);
        ItemStack safe = ResidentRules.waterBottle(3);
        helper.assertTrue(afterFirst.count(safe) == 1 && afterSecond.count(safe) == 5
                        && afterFirst.count(new ItemStack(Items.CARROT)) == 9
                        && afterSecond.count(new ItemStack(Items.CARROT)) == 1,
                "Resident cycle must transfer real goods without creating or deleting them");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void residentCycleCanCraftAtExchange(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.FURNACE);
        Villager grower = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        Villager burner = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
        ResidentRules.setWorksite(grower, grower.blockPosition());
        ResidentRules.setWorksite(burner, grower.blockPosition());
        ResidentState first = fresh(grower);
        ResidentState second = fresh(burner);
        first.setNeeds(18, 18, 18);
        second.setNeeds(5, 18, 18);
        first.add(new ItemStack(Items.CARROT, 10));
        second.add(new ItemStack(Items.OAK_LOG, 4));
        helper.assertTrue(ResidentBarter.offers(helper.getLevel(), burner).stream()
                        .anyMatch(o -> o.result().is(Items.CHARCOAL) && o.recipe() != null),
                "Furnace resident must advertise charcoal from logs and fuel");
        helper.assertTrue(ResidentExchange.settle(helper.getLevel(), grower),
                "The cycle must craft charcoal when the other resident wants it");
        helper.assertTrue(ResidentState.of(grower).count(new ItemStack(Items.CHARCOAL)) == 1
                        && ResidentState.of(burner).count(new ItemStack(Items.OAK_LOG)) == 2
                        && ResidentState.of(burner).count(new ItemStack(Items.CARROT)) == 1,
                "Trade-time furnace crafting must consume one log and one fuel, then transfer goods");
        helper.succeed();
    }

    private static ResidentState fresh(Villager villager) {
        ResidentState state = ResidentState.of(villager);
        List<ItemStack> empty = new ArrayList<>();
        for (int i = 0; i < 24; i++) empty.add(ItemStack.EMPTY);
        state.replaceItems(empty);
        return state;
    }
}

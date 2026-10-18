package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.mixin.FloatingEntityAccessor;
import com.bettercontent.spiritcommerce.registry.SpiritProfessions;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.bettercontent.spiritcommerce.spirit.SpiritAcquisition;
import com.mojang.authlib.GameProfile;
import com.sammy.malum.common.entity.spirit.SpiritItemEntity;
import com.sammy.malum.core.systems.recipe.SpiritWithCount;
import com.sammy.malum.registry.common.SpiritTypeRegistry;
import com.sammy.malum.registry.common.item.ItemRegistry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;

@PrefixGameTestTemplate(false)
public final class WanderingTraderGameTests {
    private WanderingTraderGameTests() {}

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void actualCampFrustumBoundsContainEveryMeshFacing(final GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        helper.setBlock(relative.below(), Blocks.STONE);
        for (Direction facing : java.util.List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            helper.setBlock(relative, TraderCampRegistries.POST.get().defaultBlockState()
                    .setValue(TraderCampPostBlock.FACING, facing));
            BlockPos absolute = helper.absolutePos(relative);
            var post = (TraderCampPostBlockEntity) helper.getLevel().getBlockEntity(absolute);
            helper.assertTrue(post != null, "Native camp post block entity must exist");
            AABB actual = post.getRenderBoundingBox();
            helper.assertTrue(actual.equals(TraderCampPostBlockEntity.renderBounds(absolute)),
                    "Forge frustum box must use the full camp render bounds");
            double yaw = Math.toRadians(TraderCampAwningGeometry.yawFor(facing.getStepX(), facing.getStepZ()));
            double sin = Math.sin(yaw), cos = Math.cos(yaw);
            for (var face : java.util.stream.Stream.concat(TraderCampAwningGeometry.CLOTH.stream(),
                    TraderCampAwningGeometry.WOOD.stream()).toList()) {
                for (var vertex : face.vertices()) {
                    double x = absolute.getX() + 0.5 + vertex.x() * cos + vertex.z() * sin;
                    double z = absolute.getZ() + 0.5 - vertex.x() * sin + vertex.z() * cos;
                    helper.assertTrue(actual.contains(x, absolute.getY() + vertex.y(), z),
                            "Actual Forge frustum bounds exclude camp geometry for " + facing);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void malumRecipesUseEconomySpirits(final GameTestHelper helper) {
        var arcane = new SpiritWithCount(SpiritTypeRegistry.ARCANE_SPIRIT, 2);
        helper.assertTrue(arcane.getItem() == CurrencyItems.item(com.bettercontent.spiritcommerce.spirit.CurrencyIdentity.WORK).get(),
                "Arcane-aspect recipes must display and require Work spirits");
        helper.assertTrue(arcane.matches(new ItemStack(CurrencyItems.item(com.bettercontent.spiritcommerce.spirit.CurrencyIdentity.WORK).get(), 2)),
                "Work spirits must satisfy Malum's Arcane requirement");
        helper.assertTrue(!arcane.matches(new ItemStack(ItemRegistry.ARCANE_SPIRIT.get(), 2)),
                "Ordinary native Malum spirits must no longer satisfy the active recipe");
        helper.assertTrue(new SpiritWithCount(SpiritTypeRegistry.ELDRITCH_SPIRIT, 1).getItem()
                        == ItemRegistry.ELDRITCH_SPIRIT.get(), "Eldritch must stay native Malum");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void themedIdentityIsStored(final GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(2, 2, 2));
        WanderingTraderVisits.applyTheme(trader, WanderingTraderTheme.INFERNAL, true);
        if (!"infernal".equals(trader.getPersistentData().getString(WanderingTraderVisits.THEME_TAG))) {
            helper.fail("Expected the spirit theme to persist");
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void scheduledTraderGetsMovableAwningPost(final GameTestHelper helper) {
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(2, 2, 2));
        WanderingTraderVisits.applyTheme(trader, WanderingTraderTheme.AQUEOUS, true);
        WanderingTraderScheduleData schedule = WanderingTraderScheduleData.get(helper.getLevel());
        schedule.completeVisit(helper.getLevel().getGameTime(), 1000, trader.getUUID());
        if (!TraderCampService.createForVisit(trader, WanderingTraderTheme.AQUEOUS)) {
            helper.fail("Scheduled trader could not find a safe campsite");
            return;
        }
        BlockPos original = BlockPos.of(trader.getPersistentData().getLong(TraderCampService.CAMP_POS_TAG));
        if (!(helper.getLevel().getBlockEntity(original) instanceof TraderCampPostBlockEntity post)
                || !trader.getUUID().equals(post.traderId()) || !"aqueous".equals(post.themeId())) {
            helper.fail("Camp post did not retain the linked trader and theme");
            return;
        }

        var stored = post.saveForItem();
        BlockPos moved = original.offset(3, 0, 2);
        helper.getLevel().setBlock(original, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().getChunkAt(moved);
        // CampPosTag contains absolute world coordinates. GameTestHelper.setBlock
        // interprets its positions relative to the structure, so mutate the level
        // directly here to place the replacement at the recorded absolute position.
        helper.getLevel().setBlockAndUpdate(moved.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(moved, TraderCampRegistries.POST.get().defaultBlockState()
                .setValue(TraderCampPostBlock.FACING, Direction.EAST));
        if (!(helper.getLevel().getBlockEntity(moved) instanceof TraderCampPostBlockEntity movedPost)) {
            helper.fail("Re-placed camp post has no block entity at " + moved
                    + " (state=" + helper.getLevel().getBlockState(moved)
                    + ", support=" + helper.getLevel().getBlockState(moved.below()) + ")");
            return;
        }
        movedPost.load(stored);
        movedPost.retargetLinkedTrader();
        TraderCampData.get(helper.getLevel()).setTarget(trader.getUUID(),
                helper.getLevel().dimension().location().toString(), moved, Direction.EAST);
        BlockPos destination = moved.relative(Direction.EAST);
        if (trader.getPersistentData().getLong(TraderCampService.CAMP_POS_TAG) != moved.asLong()
                || !TraderCampService.moveToCamp(trader)
                || trader.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(destination)) > 2.25D) {
            helper.fail("Moving the post did not retarget and move its trader");
            return;
        }

        var offers = trader.getOffers();
        for (MerchantOffer offer : offers) {
            while (!offer.isOutOfStock()) offer.increaseUses();
        }
        if (!TraderCampService.stockEmpty(trader, helper.getLevel())) {
            helper.fail("Trader stock was not recognized as empty");
            return;
        }
        TraderCampService.depart(trader, helper.getLevel());
        if (!trader.isRemoved() || !helper.getLevel().getBlockState(moved).isAir()
                || schedule.activeTraderId() != null) {
            helper.fail("Sold-out trader did not leave and remove its post");
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void everyThemedTraderHasThirteenMatchingGoodsAndEggs(final GameTestHelper helper) {
        for (WanderingTraderTheme theme : WanderingTraderTheme.values()) {
            WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(2, 2, 2));
            WanderingTraderVisits.applyTheme(trader, theme, false);
            MerchantOffers offers = trader.getOffers();
            long eggs = offers.stream().map(MerchantOffer::getResult)
                    .filter(stack -> stack.is(Items.VILLAGER_SPAWN_EGG)).count();
            long fontMaps = offers.stream().filter(offer -> offer.getResult().getTag() != null
                    && offer.getResult().getTag().contains("better_dimension_fonts:font_definition_id")).count();
            boolean matchingPayment = offers.stream().allMatch(offer ->
                    offer.getBaseCostA().is(CurrencyItems.item(theme.currencyIdentity()).get()));
            MerchantOffer eggOffer = offers.stream().filter(offer -> offer.getResult().is(Items.VILLAGER_SPAWN_EGG))
                    .findFirst().orElse(null);
            String profession = eggOffer == null ? "" : eggOffer.getResult().getOrCreateTagElement("EntityTag")
                    .getCompound("VillagerData").getString("profession");
            String expectedProfession = SpiritProfessions.definition(theme.spirit()).profession().getId().toString();
            if (offers.size() != 15 + fontMaps || fontMaps > 1 || eggs != 1
                    || !matchingPayment || !expectedProfession.equals(profession)) {
                helper.fail("Invalid " + theme.id() + " market: offers=" + offers.size()
                        + " eggs=" + eggs + " fontMaps=" + fontMaps
                        + " payment=" + matchingPayment + " profession=" + profession);
                return;
            }
            trader.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void spiritVillagersHaveReadableNames(final GameTestHelper helper) {
        for (WanderingTraderTheme theme : WanderingTraderTheme.values()) {
            Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
            villager.setVillagerData(villager.getVillagerData()
                    .setProfession(SpiritProfessions.definition(theme.spirit()).profession().get()));
            String expectedName = SpiritProfessions.definition(theme.spirit()).fallbackName();
            if (!expectedName.equals(villager.getName().getString())) {
                helper.fail("Unreadable " + theme.id() + " villager name: " + villager.getName().getString());
                return;
            }
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void creditedKillDropsPhysicalSpiritsAtVictim(final GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("spirit-economy-gametest".getBytes()), "spirit-economy-test"));
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        com.sammy.malum.common.capability.MalumLivingEntityDataCapability.getCapability(zombie)
                .soulData.exposedSoulDuration = 100.0F;
        zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
        AABB bounds = zombie.getBoundingBox().inflate(2.0D);
        helper.succeedWhen(() -> {
            var spirits = helper.getLevel().getEntitiesOfClass(SpiritItemEntity.class, bounds);
            var currency = spirits.stream().filter(spirit -> CurrencyIdentity.fromItemId(
                    ForgeRegistries.ITEMS.getKey(spirit.getItem().getItem())) != null).toList();
            helper.assertTrue(!currency.isEmpty(), "Credited hostile kill did not release currency at its victim");
            helper.assertTrue(currency.stream().allMatch(spirit -> {
                ItemStack delivered = spirit.getItem().copy();
                SpiritAcquisition.removeGroupedPickupMarker(delivered);
                return !delivered.hasTag();
            }), "Collected spirits must carry no delivery receipts or other stack-blocking NBT");
            helper.assertTrue(currency.stream().allMatch(spirit -> spirit.getItem().getMaxStackSize() == 64),
                    "Economy spirits must stack like ordinary Malum spirits");
        });
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void emeraldOffersAreRemoved(final GameTestHelper helper) {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new net.minecraft.world.item.ItemStack(Items.EMERALD),
                new net.minecraft.world.item.ItemStack(Items.BREAD), 1, 0, 0.0F));
        MerchantCurrencyPolicy.normalize(offers);
        if (!offers.isEmpty()) {
            helper.fail("Emerald offer survived spirit-only normalization");
            return;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void plagueDoctorUsesDailySpiritOddities(final GameTestHelper helper) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(PlagueDoctorCatalogue.PLAGUE_DOCTOR);
        if (type == null) {
            helper.fail("Rats Plague Doctor was not available in the GameTest runtime");
            return;
        }
        Entity created = type.create(helper.getLevel());
        if (!(created instanceof AbstractVillager doctor)) {
            helper.fail("Rats Plague Doctor was not an AbstractVillager");
            return;
        }
        doctor.moveTo(helper.absolutePos(new BlockPos(2, 2, 2)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(doctor);
        MerchantOffers offers = doctor.getOffers();
        boolean spiritPayments = offers.stream().allMatch(offer -> {
            var id = ForgeRegistries.ITEMS.getKey(offer.getBaseCostA().getItem());
            return id != null && com.bettercontent.spiritcommerce.spirit.CurrencyIdentity.fromItemId(id) != null;
        });
        long uniqueResults = offers.stream().map(offer -> ForgeRegistries.ITEMS.getKey(offer.getResult().getItem()))
                .distinct().count();
        if (offers.size() != 8 || uniqueResults != 8 || !spiritPayments || !AuthoredTradeSignals.isAuthored(doctor)) {
            helper.fail("Invalid plague doctor catalogue: offers=" + offers.size()
                    + " unique=" + uniqueResults + " spiritPayments=" + spiritPayments);
            return;
        }

        MerchantOffer exhausted = offers.get(0);
        while (!exhausted.isOutOfStock()) exhausted.increaseUses();
        long currentDay = doctor.getPersistentData().getLong(PlagueDoctorCatalogue.DAY_TAG);
        helper.runAfterDelay(5, () -> {
            if (!exhausted.isOutOfStock()) {
                helper.fail("Rats native restock refreshed an authored offer during the same day");
                return;
            }
            PlagueDoctorCatalogue.ensureOffers(doctor, offers, currentDay + 1L);
            if (offers.size() != 8 || offers.stream().anyMatch(MerchantOffer::isOutOfStock)
                    || doctor.getPersistentData().getLong(PlagueDoctorCatalogue.DAY_TAG) != currentDay + 1L) {
                helper.fail("Plague Doctor did not receive fresh stock for the next day");
                return;
            }
            doctor.discard();
            helper.succeed();
        });
    }
}

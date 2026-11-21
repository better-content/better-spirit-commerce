package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.mixin.FloatingEntityAccessor;
import com.bettercontent.spiritcommerce.registry.SpiritProfessions;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.sammy.malum.core.handlers.SpiritHarvestHandler;
import com.mojang.authlib.GameProfile;
import com.sammy.malum.common.entity.spirit.SpiritItemEntity;
import com.sammy.malum.common.block.curiosities.ritual_plinth.RitualPlinthBlockEntity;
import com.sammy.malum.common.item.spirit.RitualShardItem;
import com.sammy.malum.core.systems.ritual.MalumRitualTier;
import com.sammy.malum.core.systems.ritual.MalumRitualType;
import com.sammy.malum.registry.common.RitualRegistry;
import com.sammy.malum.registry.common.block.BlockRegistry;
import com.sammy.malum.core.systems.recipe.SpiritWithCount;
import com.sammy.malum.registry.common.SpiritTypeRegistry;
import com.sammy.malum.registry.common.item.ItemRegistry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
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
        helper.assertTrue(arcane.getItem() == CurrencyItems.item(com.bettercontent.spiritcommerce.spirit.CurrencyIdentity.POWER).get(),
                "Arcane-aspect native type resolves to the real Power reagent");
        helper.assertTrue(new SpiritWithCount(SpiritTypeRegistry.INFERNAL_SPIRIT, 2).getItem() == arcane.getItem(),
                "Infernal and Arcane resolve to the same Power reagent, not two required currencies");
        helper.assertTrue(arcane.matches(new ItemStack(CurrencyItems.item(com.bettercontent.spiritcommerce.spirit.CurrencyIdentity.POWER).get(), 2)),
                "Power spirit satisfies Malum's Arcane requirement");
        helper.assertTrue(!arcane.matches(new ItemStack(ItemRegistry.ARCANE_SPIRIT.get(), 2)),
                "Ordinary native Malum spirits must no longer satisfy the active recipe");
        helper.assertTrue(new SpiritWithCount(SpiritTypeRegistry.ELDRITCH_SPIRIT, 1).getItem()
                        == ItemRegistry.ELDRITCH_SPIRIT.get(), "Eldritch must stay native Malum");
        helper.assertTrue(new SpiritWithCount(SpiritTypeRegistry.UMBRAL_SPIRIT, 1).getItem()
                        == ItemRegistry.UMBRAL_SPIRIT.get(), "Umbral must stay native Malum");
        var physical = SpiritTypeRegistry.SPIRITS.values().stream()
                .map(type -> type.spiritShard.get()).collect(java.util.stream.Collectors.toSet());
        helper.assertTrue(physical.size() == 7,
                "Nine internal native property/ritual types must yield five core plus two specialist physical reagents");
        helper.assertTrue(SpiritTypeRegistry.SPIRITS.size() == 9
                && SpiritTypeRegistry.SPIRITS.get("arcane") == SpiritTypeRegistry.ARCANE_SPIRIT
                && SpiritTypeRegistry.SPIRITS.get("earthen") == SpiritTypeRegistry.EARTHEN_SPIRIT,
                "Do not prune native map entries: ritual lookups and index paths still need original types");
        var propertyValues = SpiritTypeRegistry.SPIRIT_TYPE_PROPERTY.getPossibleValues();
        helper.assertTrue(propertyValues.contains("arcane") && propertyValues.contains("earthen")
                && propertyValues.contains("infernal") && !propertyValues.contains("power")
                && !propertyValues.contains("endurance"),
                "Immutable native blockstate property admits old identifiers, not invented core names");
        helper.assertTrue(SpiritTypeRegistry.getIndexForSpiritType(SpiritTypeRegistry.ARCANE_SPIRIT) >= 0
                && SpiritTypeRegistry.getIndexForSpiritType(SpiritTypeRegistry.EARTHEN_SPIRIT) >= 0,
                "Internal native aliases retain stable property/ritual indexes; no new blockstate IDs");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void postScriptCanonicalJsonDeserializesAsOneRealNativePowerRequirement(final GameTestHelper helper) {
        // The pinned KubeJS final hook runs after RecipeJS.serialize() and before
        // RecipePlatformHelper.fromJson. Give that same post-script object to the
        // actual native Malum serializer; bulk-map-only tests cannot establish this.
        var afterScript = com.google.gson.JsonParser.parseString("{\"type\":\"malum:spirit_focusing\","
                + "\"time\":100,\"durabilityCost\":4,\"input\":{\"item\":\"minecraft:stone\"},"
                + "\"output\":{\"item\":\"minecraft:diamond\",\"count\":3},"
                + "\"spirits\":[{\"type\":\"arcane\",\"count\":2},"
                + "{\"type\":\"infernal\",\"count\":3}]}").getAsJsonObject();
        var finalJson = com.bettercontent.spiritcommerce.recipe.CoinRecipeFilter.canonicalizeSerialized(afterScript);
        var recipe = new com.sammy.malum.common.recipe.SpiritFocusingRecipe.Serializer().fromJson(
                new net.minecraft.resources.ResourceLocation(BetterSpiritCommerce.MOD_ID, "post_script_power"), finalJson);
        helper.assertTrue(recipe != null && recipe.spirits.size() == 1
                && recipe.spirits.get(0).type == SpiritTypeRegistry.INFERNAL_SPIRIT
                && recipe.spirits.get(0).count == 5
                && recipe.spirits.get(0).getItem() == CurrencyItems.item(CurrencyIdentity.POWER).get(),
                "Native serializer must receive one real Power reagent costing 2+3, not two separate spirit entries");
        helper.assertTrue(recipe.output.is(Items.DIAMOND) && recipe.output.getCount() == 3
                && recipe.time == 100 && recipe.durabilityCost == 4,
                "Native serializer must preserve exact post-script output and nonspirit costs");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void twoNativeRitualsPerCollapsedReagentStillSelectTheirOwnPlinthIdentity(final GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("ritual-identity-gametest".getBytes()), "ritual-identity"));
        MalumRitualType arcane = RitualRegistry.RITUAL_OF_MANABOUND_ENHANCEMENT;
        MalumRitualType infernal = RitualRegistry.RITUAL_OF_CTHONIC_EXCHANGE;
        MalumRitualType aqueous = RitualRegistry.RITUAL_OF_MARINE_SPOIL;
        MalumRitualType earthen = RitualRegistry.RITUAL_OF_TERRAN_UNEARTHING;
        helper.assertTrue(arcane.spirit != infernal.spirit
                && arcane.spirit.spiritShard.get() == infernal.spirit.spiritShard.get()
                && arcane.spirit.spiritShard.get() == CurrencyItems.item(CurrencyIdentity.POWER).get(),
                "Two different native ritual types share a physical Power reagent, not a ritual identifier");
        helper.assertTrue(aqueous.spirit != earthen.spirit
                && aqueous.spirit.spiritShard.get() == earthen.spirit.spiritShard.get()
                && aqueous.spirit.spiritShard.get() == CurrencyItems.item(CurrencyIdentity.ENDURANCE).get(),
                "Two different native ritual types share a physical Endurance reagent");
        selectRitual(helper, player, new BlockPos(2, 2, 2), arcane);
        selectRitual(helper, player, new BlockPos(4, 2, 2), infernal);
        selectRitual(helper, player, new BlockPos(2, 2, 2), aqueous);
        selectRitual(helper, player, new BlockPos(4, 2, 2), earthen);
        helper.succeed();
    }

    private static void selectRitual(final GameTestHelper helper, final ServerPlayer player,
                                     final BlockPos position, final MalumRitualType requested) {
        // Replace the plinth between pairs: selection is from the actual native shard
        // and native plinth handler, not from a copied NBT tag or a default reagent.
        helper.setBlock(position, Blocks.AIR);
        helper.setBlock(position, BlockRegistry.RITUAL_PLINTH.get());
        var plinth = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        helper.assertTrue(plinth instanceof RitualPlinthBlockEntity, "Native plinth must exist");
        RitualPlinthBlockEntity ritualPlinth = (RitualPlinthBlockEntity) plinth;
        ItemStack shard = new ItemStack(ItemRegistry.RITUAL_SHARD.get());
        shard.setTag(requested.createShardNBT(MalumRitualTier.DIM));
        helper.assertTrue(requested.identifier.toString().equals(shard.getTag().getString("stored_ritual"))
                && RitualShardItem.getRitualType(shard) == requested,
                "Native resolver must retain the exact stored_ritual identity");
        player.setItemInHand(InteractionHand.MAIN_HAND, shard);
        ritualPlinth.onUse(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ritualPlinth.ritualType == requested && ritualPlinth.setupComplete,
                "Native plinth selected the requested ritual by shard NBT rather than collapsed physical reagent");
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void themedIdentityIsStored(final GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(2, 2, 2));
        WanderingTraderVisits.applyTheme(trader, WanderingTraderTheme.INFERNAL, true);
        if (!"power".equals(trader.getPersistentData().getString(WanderingTraderVisits.THEME_TAG))) {
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
                || !trader.getUUID().equals(post.traderId()) || !"endurance".equals(post.themeId())) {
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
    public static void everyThemedTraderHasItsStableSampleOfMatchingGoodsAndSeparateEggs(final GameTestHelper helper) {
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
            int core = WanderingTraderCatalogue.selectedRows(trader, theme.spirit()).size();
            if (core > 13 || offers.size() != core + 1 + fontMaps || fontMaps > 1 || eggs != 1
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
    public static void everySpiritVillagerGetsNativeTradeListingsWithoutResettingSavedStock(final GameTestHelper helper) {
        for (SpiritKind kind : SpiritKind.values()) {
            Villager villager = EntityType.VILLAGER.create(helper.getLevel());
            helper.assertTrue(villager != null, "Could not create a villager for " + kind);
            villager.setVillagerData(villager.getVillagerData()
                    .setProfession(SpiritProfessions.definition(kind).profession().get()));
            MerchantOffers offers = villager.getOffers();
            var nativeTrades = net.minecraft.world.entity.npc.VillagerTrades.TRADES.get(
                    SpiritProfessions.definition(kind).profession().get());
            helper.assertTrue(offers.size() == 2, "Missing registered native novice listings for " + kind
                    + ": " + offers.size() + ", registered=" + (nativeTrades == null ? "null" : nativeTrades.keySet())
                    + ", level1=" + (nativeTrades == null || nativeTrades.get(1) == null ? "null" : nativeTrades.get(1).length)
                    + ", level=" + villager.getVillagerData().getLevel());
            helper.assertTrue(offers.stream().allMatch(offer -> offer.getBaseCostA().is(
                    CurrencyItems.item(kind.currencyIdentity()).get()) && offer.getCostB().isEmpty()
                    && !offer.getResult().isEmpty()), "Villager did not sell for its matching spirit: " + kind);
            MerchantOffer selected = offers.get(0);
            selected.increaseUses();
            CompoundTag saved = villager.saveWithoutId(new CompoundTag());
            helper.assertTrue(saved.contains("Offers"), "Used specialist stock was not saved: " + kind);
            Villager restored = EntityType.VILLAGER.create(helper.getLevel());
            restored.load(saved);
            helper.assertTrue(restored.getOffers().size() == 2 && restored.getOffers().get(0).getUses() == 1
                    && ItemStack.isSameItemSameTags(selected.getResult(), restored.getOffers().get(0).getResult()),
                    "Reopening a saved specialist rerolled offers or reset spent stock: " + kind);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 100)
    public static void emptySpecialistFromPreviousVersionRecoversAfterSaveAndLoad(final GameTestHelper helper) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(SpiritProfessions.definition(SpiritKind.RENEWAL).profession().get()));
        villager.setOffers(new MerchantOffers());
        CompoundTag saved = villager.saveWithoutId(new CompoundTag());
        helper.assertTrue(!saved.contains("Offers"), "Vanilla must not persist a wiped, empty offers list");
        Villager restored = EntityType.VILLAGER.create(helper.getLevel());
        restored.load(saved);
        helper.assertTrue(restored.getOffers().size() == 2 && restored.getOffers().stream().allMatch(offer ->
                offer.getBaseCostA().is(CurrencyItems.item(CurrencyIdentity.RENEWAL).get())),
                "An empty saved specialist did not regenerate its native listings after restart");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty", timeoutTicks = 160)
    public static void creditedKillReleasesMalumMappedSpiritsAtVictimImmediately(final GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("spirit-economy-gametest".getBytes()), "spirit-economy-test"));
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        com.sammy.malum.common.capability.MalumLivingEntityDataCapability.getCapability(zombie)
                .soulData.exposedSoulDuration = 100.0F;
        var nativeDrops = SpiritHarvestHandler.getSpawnedSpirits(zombie, player, ItemStack.EMPTY);
        var expected = new java.util.EnumMap<CurrencyIdentity, Integer>(CurrencyIdentity.class);
        for (ItemStack stack : nativeDrops) {
            var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            CurrencyIdentity identity = CurrencyIdentity.fromLegacyNativeSpirit(id);
            if (identity == null) identity = CurrencyIdentity.fromItemId(id);
            if (identity != null) expected.merge(identity, stack.getCount(), Integer::sum);
        }
        helper.assertTrue(!expected.isEmpty(), "Malum must return a mapped zombie spirit for this fixture");
        zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
        AABB bounds = zombie.getBoundingBox().inflate(3.0D);
        var spirits = helper.getLevel().getEntitiesOfClass(SpiritItemEntity.class, bounds);
        var actual = new java.util.EnumMap<CurrencyIdentity, Integer>(CurrencyIdentity.class);
        for (SpiritItemEntity spirit : spirits) {
            helper.assertTrue(player.getUUID().equals(spirit.ownerUUID), "Spirit must target the credited player");
            var itemId = ForgeRegistries.ITEMS.getKey(spirit.getItem().getItem());
            CurrencyIdentity identity = CurrencyIdentity.fromItemId(itemId);
            if (identity == null) {
                // Native Malum can release specialist/exotic stacks in parallel;
                // they must remain native, not become ordinary currency.
                helper.assertTrue(CurrencyIdentity.fromLegacyNativeSpirit(itemId) == null,
                        "Ordinary Malum spirit escaped the commerce mapping: " + itemId);
                continue;
            }
            actual.merge(identity, spirit.getItem().getCount(), Integer::sum);
        }
        helper.assertTrue(actual.equals(expected), "Immediate drops must match Malum's mob types and counts: "
                + actual + " vs " + expected);
        var initialIds = spirits.stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());
        helper.runAfterDelay(120, () -> {
            var later = helper.getLevel().getEntitiesOfClass(SpiritItemEntity.class, bounds.inflate(12.0D)).stream()
                    .filter(spirit -> player.getUUID().equals(spirit.ownerUUID))
                    .filter(spirit -> !initialIds.contains(spirit.getUUID())).toList();
            helper.assertTrue(later.isEmpty(), "A delayed second spirit release appeared after the kill");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void excludedMobsDoNotReleaseSpirits(final GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "excluded-spirit-test"));
        var spawnerMob = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        var soullessMob = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 2));
        var spawnerSoul = com.sammy.malum.common.capability.MalumLivingEntityDataCapability
                .getCapability(spawnerMob).soulData;
        spawnerSoul.exposedSoulDuration = 100.0F;
        spawnerSoul.spawnerSpawned = true;
        var soullessSoul = com.sammy.malum.common.capability.MalumLivingEntityDataCapability
                .getCapability(soullessMob).soulData;
        soullessSoul.exposedSoulDuration = 100.0F;
        soullessSoul.soulless = true;
        spawnerMob.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
        soullessMob.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(SpiritItemEntity.class,
                spawnerMob.getBoundingBox().inflate(5.0D)).isEmpty(),
                "Spawner-origin and soulless mobs must not release spirits");
        helper.succeed();
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

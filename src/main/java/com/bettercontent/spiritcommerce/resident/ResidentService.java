package com.bettercontent.spiritcommerce.resident;

import com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Loaded-resident needs and visible resource gathering. */
public final class ResidentService {
    private static final int RADIUS = 12;
    private ResidentService() {}

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof Villager && !entity.getPersistentData().getBoolean("better_spirit_commerce:seeded")) {
            entity.getPersistentData().putBoolean("better_spirit_commerce:seeded", true);
            ResidentState state = ResidentState.of(entity);
            addGenerated(state, new ItemStack(Items.CARROT, 3));
            addGenerated(state, new ItemStack(Items.GLASS_BOTTLE, 3));
            addGenerated(state, ResidentRules.waterBottle(3).copyWithCount(2));
            addGenerated(state, new ItemStack(Items.COAL, 2));
        }
        if (entity.getPersistentData().getBoolean("better_spirit_commerce:resident_rat")
                && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                .toString().equals("rats:rat")) {
            if (entity instanceof net.minecraft.world.entity.TamableAnimal rat) rat.setTame(true);
        }
        if (ResidentRules.isResident(entity)
                && !entity.getPersistentData().getBoolean("better_spirit_commerce:tools_seeded")) {
            entity.getPersistentData().putBoolean("better_spirit_commerce:tools_seeded", true);
            ResidentState state = ResidentState.of(entity);
            addGenerated(state, new ItemStack(Items.STONE_AXE));
            addGenerated(state, new ItemStack(Items.STONE_PICKAXE));
        }
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !ResidentRules.isResident(entity)
                || entity.tickCount % 40 != 0) return;
        ResidentState state = ResidentState.of(entity);
        if (entity.tickCount % 2400 == 0) {
            boolean shelter = ResidentRules.hasShelter(level, entity.blockPosition());
            state.setNeeds(state.food() - 1, state.water() - 1, state.rest() + (shelter ? 2 : -2));
            consume(state);
            if (state.food() >= 14 && state.water() >= 14 && state.rest() >= 14
                    && entity.getHealth() < entity.getMaxHealth()) entity.heal(1);
            if (state.food() == 0 || state.water() == 0 || state.rest() == 0)
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 2600, 0));
        }
        if (entity.tickCount % 200 == 0) {
            if (state.water() < 14) purifyWater(level, entity, state);
            if (Math.floorMod(entity.tickCount / 200 + entity.getUUID().hashCode(), 5) == 0)
                ResidentExchange.settle(level, entity);
            if (entity instanceof PathfinderMob mob) {
                seekShelter(level, mob, state);
                gather(level, mob, state);
            }
        }
    }

    private static void consume(ResidentState state) {
        if (state.food() < 14) for (ItemStack stack : state.items()) {
            if (stack.isEmpty() || stack.getFoodProperties(null) == null) continue;
            ItemStack one = stack.copyWithCount(1);
            if (state.remove(one)) state.setNeeds(state.food() + stack.getFoodProperties(null).getNutrition(),
                    state.water(), state.rest());
            break;
        }
        if (state.water() < 14) for (ItemStack stack : state.items()) {
            if (!ResidentRules.isSafeWater(stack)) continue;
            ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
            if (!RenewableAcquisitionPolicy.allows(empty)) continue;
            if (state.remove(stack.copyWithCount(1))) {
                state.add(empty);
                state.setNeeds(state.food(), state.water() + 6, state.rest());
            }
            break;
        }
    }

    private static void purifyWater(ServerLevel level, LivingEntity entity, ResidentState state) {
        boolean furnace = false;
        for (BlockPos pos : BlockPos.betweenClosed(entity.blockPosition().offset(-8, -2, -8),
                entity.blockPosition().offset(8, 2, 8))) {
            if (level.getBlockState(pos).is(Blocks.FURNACE)) { furnace = true; break; }
        }
        if (!furnace) return;
        ItemStack fuel = ItemStack.EMPTY;
        for (ItemStack stack : state.items()) {
            if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)
                    || stack.is(ItemTags.LOGS)) { fuel = stack.copyWithCount(1); break; }
        }
        if (fuel.isEmpty()) return;
        for (var recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!ResidentRules.isSafeWater(result) || !state.canAdd(result)
                    || recipe.getIngredients().isEmpty() || !RenewableAcquisitionPolicy.allows(result)) continue;
            for (ItemStack input : state.items()) {
                if (input.isEmpty() || !recipe.getIngredients().get(0).test(input)) continue;
                if (!RenewableAcquisitionPolicy.allows(result)) return;
                if (!state.remove(input.copyWithCount(1)) || !state.remove(fuel)) return;
                state.add(result.copy());
                state.setDoing("Purifying water");
                state.event("purified " + result.getHoverName().getString());
                return;
            }
        }
    }

    private static void gather(ServerLevel level, PathfinderMob mob, ResidentState state) {
        if (!mob.getNavigation().isDone()) return;
        BlockPos worksite = ResidentRules.worksite(mob);
        if (mob.blockPosition().distSqr(worksite) > 18 * 18) {
            mob.getNavigation().moveTo(worksite.getX() + .5, worksite.getY(), worksite.getZ() + .5, .6);
            state.setDoing("Returning to the village");
            return;
        }
        if (state.water() < 15 && collectWater(level, mob, state)) return;
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos center = mob.blockPosition();
        ResidentTerrainData terrain = ResidentTerrainData.get(level);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-RADIUS, -2, -RADIUS),
                center.offset(RADIUS, 2, RADIUS))) {
            if (!level.hasChunkAt(p)) continue;
            if (p.distSqr(worksite) > 18 * 18) continue;
            BlockState block = level.getBlockState(p);
            if (mob.tickCount % 1200 == 0 && block.getBlock() instanceof CropBlock crop
                    && crop.isMaxAge(block)) candidates.add(p.immutable());
            else if (state.food() >= 14 && stock(state, ItemTags.LOGS) < 8
                    && block.is(BlockTags.LOGS) && tree(level, p) && !nearWorkplace(level, p)
                    && !terrain.protectedAt(p)) candidates.add(p.immutable());
            else if (state.food() >= 14 && quarry(block) && materialDemand(state, block)
                    && exposed(level, p) && (!block.requiresCorrectToolForDrops()
                    || hasToolFor(mob, state, block))
                    && p.distSqr(center) > 25 && !nearWorkplace(level, p)
                    && !terrain.protectedAt(p)) candidates.add(p.immutable());
            if (candidates.size() >= 32) break;
        }
        candidates.sort(Comparator.comparingDouble(center::distSqr));
        if (candidates.isEmpty()) { state.setDoing(ResidentRules.needLabel(state,
                ResidentRules.hasShelter(level, center))); return; }
        BlockPos target = candidates.get(0);
        if (center.distSqr(target) > 6) {
            mob.getNavigation().moveTo(target.getX() + .5, target.getY(), target.getZ() + .5, .6);
            state.setDoing("Going to gather resources");
            return;
        }
        BlockState block = level.getBlockState(target);
        if (block.getBlock() instanceof CropBlock crop && crop.isMaxAge(block)) {
            harvestCrop(level, mob, state, target, block);
        } else if (block.is(BlockTags.LOGS) && tree(level, target)
                && !terrain.protectedAt(target)) {
            harvestTree(level, mob, state, target, block);
        } else if (quarry(block) && materialDemand(state, block)
                && exposed(level, target) && !nearWorkplace(level, target)
                && !terrain.protectedAt(target)) {
            harvestBlock(level, mob, state, target, block, "Quarrying surface material");
        }
    }

    private static boolean collectWater(ServerLevel level, PathfinderMob mob, ResidentState state) {
        if (state.count(new ItemStack(Items.GLASS_BOTTLE)) < 1) return false;
        BlockPos center = mob.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -1, -RADIUS),
                center.offset(RADIUS, 0, RADIUS))) {
            if (!level.getFluidState(pos).isSourceOfType(Fluids.WATER)) continue;
            if (center.distSqr(pos) > 8) {
                mob.getNavigation().moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, .6);
                state.setDoing("Going to collect water");
                return true;
            }
            ItemStack filled = ResidentRules.waterBottle(0);
            if (!RenewableAcquisitionPolicy.allows(filled)) return false;
            if (!state.remove(new ItemStack(Items.GLASS_BOTTLE))) return false;
            state.add(filled);
            state.setDoing("Collecting water");
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private static void harvestCrop(ServerLevel level, PathfinderMob mob, ResidentState state,
                                    BlockPos pos, BlockState block) {
        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(block, level, pos,
                level.getBlockEntity(pos), mob, mob.getMainHandItem());
        if (!RenewableAcquisitionPolicy.allowsAll(drops)) return;
        ItemStack seed = drops.stream().filter(s -> s.is(block.getBlock().asItem())).findFirst().orElse(ItemStack.EMPTY);
        if (seed.isEmpty()) return;
        seed.shrink(1);
        level.setBlockAndUpdate(pos, block.getBlock().defaultBlockState());
        for (ItemStack drop : drops) if (!drop.isEmpty()) {
            if (!state.add(drop)) net.minecraft.world.level.block.Block.popResource(level, pos, drop);
        }
        state.setDoing("Harvesting crops");
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5,
                3, .3, .3, .3, 0);
    }

    private static void harvestBlock(ServerLevel level, PathfinderMob mob, ResidentState state,
                                     BlockPos pos, BlockState block, String action) {
        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(block, level, pos,
                level.getBlockEntity(pos), mob, gatheringTool(mob, state, block));
        if (!RenewableAcquisitionPolicy.allowsAll(drops) || !equipFor(mob, state, block)) return;
        level.destroyBlock(pos, false, mob);
        for (ItemStack drop : drops) if (!state.add(drop))
            net.minecraft.world.level.block.Block.popResource(level, pos, drop);
        state.setDoing(action);
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        wearTool(mob, block, 1);
        level.sendParticles(ParticleTypes.POOF, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5,
                4, .25, .25, .25, 0);
    }

    private static void harvestTree(ServerLevel level, PathfinderMob mob, ResidentState state,
                                    BlockPos base, BlockState block) {
        while (level.getBlockState(base.below()).getBlock() == block.getBlock()
                && base.getY() > level.getMinBuildHeight() + 1) base = base.below();
        ResidentTerrainData terrain = ResidentTerrainData.get(level);
        if (terrain.protectedAt(base) || nearWorkplace(level, base)) return;
        Block sapling = block.is(Blocks.OAK_LOG) ? Blocks.OAK_SAPLING
                : block.is(Blocks.BIRCH_LOG) ? Blocks.BIRCH_SAPLING
                : block.is(Blocks.SPRUCE_LOG) ? Blocks.SPRUCE_SAPLING : Blocks.AIR;
        if (sapling == Blocks.AIR) {
            harvestBlock(level, mob, state, base, block, "Gathering timber");
            return;
        }
        List<BlockPos> logs = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 12, 3)))
            if (level.getBlockState(p).getBlock() == block.getBlock()
                    && !terrain.protectedAt(p)) logs.add(p.immutable());
        if (logs.size() > 48) return;
        List<List<ItemStack>> stagedDrops = new ArrayList<>();
        for (BlockPos pos : logs) {
            BlockState log = level.getBlockState(pos);
            List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(log, level, pos,
                    level.getBlockEntity(pos), mob, gatheringTool(mob, state, log));
            if (!RenewableAcquisitionPolicy.allowsAll(drops)) return;
            stagedDrops.add(drops);
        }
        // Validate the complete tree before equipping/debiting tools or destroying the first log.
        if (!RenewableAcquisitionPolicy.allowsAll(stagedDrops.stream().flatMap(List::stream).toList())
                || !equipFor(mob, state, block)) return;
        for (int index = 0; index < logs.size(); index++) {
            BlockPos pos = logs.get(index);
            level.destroyBlock(pos, false, mob);
            for (ItemStack drop : stagedDrops.get(index)) if (!state.add(drop))
                net.minecraft.world.level.block.Block.popResource(level, pos, drop);
        }
        if (level.getBlockState(base.below()).is(BlockTags.DIRT)
                && level.getBlockState(base).isAir()) level.setBlockAndUpdate(base, sapling.defaultBlockState());
        state.setDoing("Felling and replanting a tree");
        state.event("felled tree at " + base.toShortString());
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        wearTool(mob, block, Math.max(1, logs.size() / 4));
    }

    /** New seed/source generation only. Never use for player gifts or transfers of old finite stock. */
    static boolean addGenerated(ResidentState state, ItemStack generated) {
        return RenewableAcquisitionPolicy.allows(generated) && state.add(generated);
    }

    /** Read-only tool selection so source output veto precedes inventory/equipment debit. */
    private static ItemStack gatheringTool(PathfinderMob mob, ResidentState state, BlockState block) {
        if (mob.getMainHandItem().isCorrectToolForDrops(block)) return mob.getMainHandItem();
        for (ItemStack stack : state.items()) if (stack.isCorrectToolForDrops(block)) return stack.copyWithCount(1);
        return mob.getMainHandItem();
    }

    private static boolean hasToolFor(PathfinderMob mob, ResidentState state, BlockState block) {
        if (mob.getMainHandItem().isCorrectToolForDrops(block)) return true;
        for (ItemStack stack : state.items()) if (stack.isCorrectToolForDrops(block)) return true;
        return false;
    }

    private static boolean equipFor(PathfinderMob mob, ResidentState state, BlockState block) {
        if (mob.getMainHandItem().isCorrectToolForDrops(block)) return true;
        ItemStack selected = ItemStack.EMPTY;
        for (ItemStack stack : state.items()) if (stack.isCorrectToolForDrops(block)) {
            selected = stack.copyWithCount(1); break;
        }
        if (selected.isEmpty()) return !block.requiresCorrectToolForDrops();
        ItemStack previous = mob.getMainHandItem().copy();
        if (!state.remove(selected)) return false;
        if (!previous.isEmpty()) state.add(previous);
        mob.setItemSlot(EquipmentSlot.MAINHAND, selected);
        return true;
    }

    private static void wearTool(PathfinderMob mob, BlockState block, int amount) {
        ItemStack tool = mob.getMainHandItem();
        if (!tool.isDamageableItem() || !tool.isCorrectToolForDrops(block)) return;
        tool.hurtAndBreak(amount, mob, wearer -> wearer.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
    }

    private static int stock(ResidentState state, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        int count = 0;
        for (ItemStack item : state.items()) if (item.is(tag)) count += item.getCount();
        return count;
    }

    private static boolean materialDemand(ResidentState state, BlockState block) {
        ItemStack wanted = block.is(Blocks.STONE) ? new ItemStack(Blocks.COBBLESTONE)
                : new ItemStack(Blocks.DIRT);
        return state.count(wanted) < 8;
    }

    private static void seekShelter(ServerLevel level, PathfinderMob mob, ResidentState state) {
        if (state.rest() >= 14 || ResidentRules.hasShelter(level, mob.blockPosition())) return;
        if (level.isDay()) return;
        BlockPos worksite = ResidentRules.worksite(mob);
        for (BlockPos p : BlockPos.betweenClosed(worksite.offset(-RADIUS, -3, -RADIUS),
                worksite.offset(RADIUS, 3, RADIUS))) {
            BlockState block = level.getBlockState(p);
            if (!(block.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                    && !block.getBlock().getDescriptionId().contains("rat_hole")) continue;
            mob.getNavigation().moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, .55);
            state.setDoing("Seeking shelter");
            return;
        }
    }

    private static boolean tree(ServerLevel level, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 5, 2)))
            if (level.getBlockState(near).is(BlockTags.LEAVES)) return true;
        return false;
    }

    private static boolean quarry(BlockState block) {
        return block.is(Blocks.STONE) || block.is(Blocks.DIRT) || block.is(Blocks.GRASS_BLOCK)
                || block.getBlock().getDescriptionId().contains("regolith");
    }

    private static boolean exposed(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir();
    }

    private static boolean nearWorkplace(ServerLevel level, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 2, 3))) {
            BlockState block = level.getBlockState(near);
            if (block.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                    || level.getBlockEntity(near) != null) return true;
        }
        return false;
    }
}

package com.bettercontent.spiritcommerce.resident;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Conservative flat-site starter villages. All structures stay within one fresh chunk. */
public final class SettlementGeneration {
    private static final boolean DEBUG_SITES = Boolean.getBoolean("bettercontent.resident.debugSites");
    private record Pending(ServerLevel level, ChunkPos chunk, int retries) {}
    private static final java.util.concurrent.ConcurrentLinkedQueue<Pending> PENDING =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private SettlementGeneration() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().location().getPath().contains("ratlantis")) return;
        ChunkPos chunk = event.getChunk().getPos();
        if (Math.floorMod(chunk.x, 4) != 0 || Math.floorMod(chunk.z, 4) != 0) return;
        // Chunk Load fires during promotion to FULL. Reading the heightmap here deadlocks
        // that promotion, so process the candidate from a later server tick.
        PENDING.add(new Pending(level, chunk, 0));
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        for (int i = 0; i < 2; i++) {
            Pending site = PENDING.poll();
            if (site == null) return;
            if (!site.level().getChunkSource().hasChunk(site.chunk().x, site.chunk().z)) {
                if (site.retries() < 20) PENDING.add(new Pending(site.level(), site.chunk(), site.retries() + 1));
                continue;
            }
            generateRats(site.level(), site.chunk());
        }
    }

    @SubscribeEvent
    public static void onVillageTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                || villager.tickCount % 600 != 0) return;
        long site = ChunkPos.asLong(villager.blockPosition().getX() >> 7,
                villager.blockPosition().getZ() >> 7);
        SettlementData data = SettlementData.get(level);
        List<Villager> residents = level.getEntitiesOfClass(Villager.class,
                villager.getBoundingBox().inflate(32), Entity::isAlive);
        if (data.contains(site)) {
            if (!ResidentRules.hasWorksite(villager)) for (Villager neighbor : residents) {
                if (ResidentRules.hasWorksite(neighbor)) {
                    ResidentRules.setWorksite(villager, ResidentRules.worksite(neighbor));
                    break;
                }
            }
            return;
        }
        if (residents.size() < 3) return;
        BlockPos center = villager.blockPosition();
        for (int dx = -24; dx <= 24; dx += 8) for (int dz = -24; dz <= 24; dz += 8) {
            int x = center.getX() + dx, z = center.getZ() + dz;
            int y = safeOverworldSite(level, x, z);
            if (y == Integer.MIN_VALUE) continue;
            if (!data.mark(site)) return;
            overworldWorkshop(level, x, y, z);
            BlockPos worksite = new BlockPos(x + 3, y, z + 4);
            for (Villager resident : residents) ResidentRules.setWorksite(resident, worksite);
            return;
        }
    }

    private static int safeOverworldSite(ServerLevel level, int ox, int oz) {
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int x = ox; x < ox + 10; x++) for (int z = oz; z < oz + 10; z++) {
            BlockPos probe = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            int y = probe.getY(); low = Math.min(low, y); high = Math.max(high, y);
            Block ground = level.getBlockState(probe.below()).getBlock();
            if (ground != Blocks.DIRT && ground != Blocks.GRASS_BLOCK && ground != Blocks.STONE) return Integer.MIN_VALUE;
            for (int dy = 0; dy < 3; dy++) if (!level.getBlockState(probe.above(dy)).isAir()) return Integer.MIN_VALUE;
        }
        return high - low <= 1 ? high : Integer.MIN_VALUE;
    }

    private static void overworldWorkshop(ServerLevel level, int ox, int y, int oz) {
        for (int x = ox; x < ox + 10; x++) for (int z = oz; z < oz + 10; z++)
            level.setBlockAndUpdate(new BlockPos(x, y - 1, z), Blocks.DIRT.defaultBlockState());
        for (int x = ox; x < ox + 6; x++) for (int z = oz; z < oz + 9; z++)
            level.setBlockAndUpdate(new BlockPos(x, y + 3, z), Blocks.OAK_SLAB.defaultBlockState());
        for (int z = oz + 1; z < oz + 8; z++) station(level, ox + 1, y, z, Blocks.COBBLESTONE_WALL);
        station(level, ox + 2, y, oz + 1, Blocks.CRAFTING_TABLE);
        station(level, ox + 2, y, oz + 2, Blocks.FURNACE);
        station(level, ox + 2, y, oz + 3, block("farmersdelight:cutting_board"));
        station(level, ox + 2, y, oz + 4, block("hexerei:willow_woodcutter"));
        station(level, ox + 2, y, oz + 5, Blocks.STONECUTTER);
        station(level, ox + 2, y, oz + 6, Blocks.CAMPFIRE);
        station(level, ox + 2, y + 1, oz + 6, block("farmersdelight:cooking_pot"));
        station(level, ox + 2, y, oz + 7, block("tconstruct:part_builder"));
        station(level, ox + 3, y, oz + 7, block("tconstruct:tinker_station"));
        station(level, ox + 4, y, oz + 1, Blocks.WHITE_BED);
        farm(level, ox + 6, y, oz + 5);
    }

    private static void generateRats(ServerLevel level, ChunkPos chunk) {
        if (!level.hasChunk(chunk.x, chunk.z)) return;
        long site = ChunkPos.asLong(Math.floorDiv(chunk.x, 16), Math.floorDiv(chunk.z, 16));
        SettlementData data = SettlementData.get(level);
        if (data.contains(site)) return;
        int ox = chunk.getMinBlockX(), oz = chunk.getMinBlockZ();
        int y = safeSite(level, ox, oz);
        if (y < level.getMinBuildHeight() + 5) return;
        if (!data.mark(site)) return;
        if (DEBUG_SITES) System.out.println("BCV rat settlement accepted at " + ox + "," + y + "," + oz);
        floor(level, ox, y, oz);
        house(level, ox + 2, y, oz + 2);
        house(level, ox + 8, y, oz + 2);
        house(level, ox + 2, y, oz + 8);
        farm(level, ox + 8, y, oz + 8);
        station(level, ox + 12, y - 1, oz + 2, Blocks.DIRT);
        station(level, ox + 12, y, oz + 2, Blocks.OAK_SAPLING);
        station(level, ox + 12, y - 1, oz + 6, Blocks.DIRT);
        station(level, ox + 12, y, oz + 6, Blocks.OAK_SAPLING);
        station(level, ox + 6, y, oz + 2, Blocks.CRAFTING_TABLE);
        station(level, ox + 6, y, oz + 3, Blocks.FURNACE);
        station(level, ox + 6, y, oz + 4, block("farmersdelight:cutting_board"));
        station(level, ox + 6, y, oz + 5, block("hexerei:willow_woodcutter"));
        station(level, ox + 6, y, oz + 6, Blocks.STONECUTTER);
        station(level, ox + 6, y, oz + 7, block("tconstruct:part_builder"));
        station(level, ox + 6, y, oz + 8, block("tconstruct:tinker_station"));
        station(level, ox + 6, y, oz + 9, Blocks.CAMPFIRE);
        station(level, ox + 6, y + 1, oz + 9, block("farmersdelight:cooking_pot"));
        int[][] homes = {{3, 3}, {9, 3}, {3, 9}, {5, 4}, {11, 4}, {5, 10}};
        for (int[] home : homes) spawnRat(level, new BlockPos(ox + home[0], y, oz + home[1]));
    }

    private static int safeSite(ServerLevel level, int ox, int oz) {
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        ResidentTerrainData terrain = ResidentTerrainData.get(level);
        for (int x = ox + 2; x <= ox + 13; x++) for (int z = oz + 2; z <= oz + 13; z++) {
            int y = naturalSurface(level, terrain, x, z);
            if (y == Integer.MIN_VALUE) return y;
            low = Math.min(low, y); high = Math.max(high, y);
        }
        if (high - low > 12) {
            if (DEBUG_SITES) System.out.println("BCV rat settlement rejected slope " + low + ".." + high
                    + " at " + ox + "," + oz);
            return Integer.MIN_VALUE;
        }
        return high;
    }

    private static int naturalSurface(ServerLevel level, ResidentTerrainData terrain, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int depth = 0; depth < 16; depth++, y--) {
            BlockPos ground = new BlockPos(x, y - 1, z);
            var surface = level.getBlockState(ground);
            if (level.getBlockEntity(ground) != null || level.getFluidState(ground).isSource()
                    || terrain.protectedAt(ground)) return Integer.MIN_VALUE;
            if (surface.is(net.minecraft.tags.BlockTags.DIRT) || surface.is(Blocks.STONE)
                    || surface.is(Blocks.SAND) || surface.is(Blocks.SANDSTONE)) return y;
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(surface.getBlock()).getPath();
            if (!surface.is(net.minecraft.tags.BlockTags.PLANKS)
                    && !surface.is(net.minecraft.tags.BlockTags.WOOL)
                    && !(surface.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                    && !(surface.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                    && !(surface.getBlock() instanceof net.minecraft.world.level.block.FenceBlock)
                    && !(surface.getBlock() instanceof net.minecraft.world.level.block.SlabBlock)
                    && !(surface.getBlock() instanceof net.minecraft.world.level.block.StairBlock)
                    && !(surface.getBlock() instanceof net.minecraft.world.level.block.WallBlock)
                    && !path.contains("brick") && !path.contains("marble")
                    && !path.contains("glass") && !path.contains("concrete")) continue;
            if (DEBUG_SITES) System.out.println("BCV rat settlement rejected surface " + surface
                    + " at " + ground.toShortString());
            return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static void floor(ServerLevel level, int ox, int y, int oz) {
        ResidentTerrainData terrain = ResidentTerrainData.get(level);
        for (int x = ox + 2; x <= ox + 13; x++) for (int z = oz + 2; z <= oz + 13; z++) {
            BlockPos pos = new BlockPos(x, y - 1, z);
            int groundY = naturalSurface(level, terrain, x, z);
            if (groundY == Integer.MIN_VALUE) groundY = y;
            for (int fill = groundY;
                 fill < y; fill++) level.setBlockAndUpdate(new BlockPos(x, fill - 1, z), Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            for (int dy = 0; dy <= 6; dy++) level.setBlockAndUpdate(pos.above(dy + 1), Blocks.AIR.defaultBlockState());
        }
    }

    private static void house(ServerLevel level, int x, int y, int z) {
        Block hole = block("rats:rat_hole");
        for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) {
            boolean wall = dx == 0 || dx == 2 || dz == 0 || dz == 2;
            if (wall) {
                level.setBlockAndUpdate(new BlockPos(x + dx, y, z + dz), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x + dx, y + 1, z + dz), Blocks.OAK_PLANKS.defaultBlockState());
            }
            level.setBlockAndUpdate(new BlockPos(x + dx, y + 2, z + dz), Blocks.OAK_SLAB.defaultBlockState());
        }
        level.setBlockAndUpdate(new BlockPos(x + 1, y, z), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(x + 1, y + 1, z), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(x + 1, y, z + 2), hole.defaultBlockState());
    }

    private static void farm(ServerLevel level, int x, int y, int z) {
        for (int dx = 0; dx < 4; dx++) for (int dz = 0; dz < 4; dz++) {
            BlockPos pos = new BlockPos(x + dx, y - 1, z + dz);
            if (dx == 1 && dz == 1) {
                level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            } else {
                level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
                level.setBlockAndUpdate(pos.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            }
        }
    }

    private static void station(ServerLevel level, int x, int y, int z, Block block) {
        if (block != Blocks.AIR) level.setBlockAndUpdate(new BlockPos(x, y, z), block.defaultBlockState());
    }

    private static Block block(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        return block == null ? Blocks.AIR : block;
    }

    private static void spawnRat(ServerLevel level, BlockPos pos) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("rats", "rat"));
        if (type == null) return;
        Entity entity = type.create(level);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity rat)) return;
        rat.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        rat.getPersistentData().putBoolean("better_spirit_commerce:resident_rat", true);
        ResidentRules.setWorksite(rat, new BlockPos((pos.getX() & ~15) + 7, pos.getY(), (pos.getZ() & ~15) + 7));
        if (rat instanceof net.minecraft.world.entity.TamableAnimal tame) tame.setTame(true);
        level.addFreshEntity(rat);
        ResidentState state = ResidentState.of(rat);
        ResidentService.addGenerated(state, new ItemStack(Items.CARROT, 8));
        ResidentService.addGenerated(state, new ItemStack(Items.GLASS_BOTTLE, 8));
        ResidentService.addGenerated(state, ResidentRules.waterBottle(3).copyWithCount(4));
        ResidentService.addGenerated(state, new ItemStack(Items.COAL, 6));
        ResidentService.addGenerated(state, new ItemStack(Items.OAK_LOG, 4));
        state.setDoing("Settling in");
    }
}

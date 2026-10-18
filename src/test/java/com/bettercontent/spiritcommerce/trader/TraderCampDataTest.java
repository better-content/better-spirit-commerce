package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class TraderCampDataTest {
    @Test
    void relocatedTargetAndFacingSurviveSaveAndReload() {
        UUID trader = UUID.fromString("4b45b814-1015-4536-89c7-5aadd21917e3");
        BlockPos original = new BlockPos(321, 72, -654);
        BlockPos moved = new BlockPos(-98, 68, 207);
        TraderCampData data = new TraderCampData();

        data.setTarget(trader, "minecraft:overworld", original, Direction.SOUTH);
        data.setTarget(trader, "minecraft:overworld", moved, Direction.EAST);
        TraderCampData reloaded = TraderCampData.load(data.save(new CompoundTag()));

        assertTrue(reloaded.belongsTo(trader));
        assertEquals("minecraft:overworld", reloaded.dimension());
        assertEquals(moved, reloaded.targetPos());
        assertEquals(Direction.EAST, reloaded.facing());
    }

    @Test
    void firePositionPersistsButRelocationOrDepartureInvalidatesIt() {
        UUID trader = UUID.randomUUID();
        TraderCampData data = new TraderCampData();
        BlockPos post = new BlockPos(10, 65, 10);
        BlockPos fire = post.east(2);
        data.setTarget(trader, "minecraft:overworld", post, Direction.NORTH);
        data.setFirePos(fire);
        TraderCampData loaded = TraderCampData.load(data.save(new CompoundTag()));
        assertEquals(fire, loaded.firePos());
        loaded.setTarget(trader, "minecraft:overworld", post.east(20), Direction.NORTH);
        assertEquals(null, loaded.firePos(), "An unloaded old fire must become obsolete on retarget");
        loaded.setFirePos(fire);
        loaded.clear(trader);
        assertEquals(null, loaded.firePos());
    }

    @Test
    void clearOnlyRemovesTheMatchingTraderLink() {
        UUID trader = UUID.fromString("843c67c3-7eb8-4f47-8b14-646c7c13aade");
        TraderCampData data = new TraderCampData();
        data.setTarget(trader, "minecraft:overworld", new BlockPos(4, 65, 9), Direction.SOUTH);

        data.clear(UUID.randomUUID());
        assertTrue(data.belongsTo(trader));

        data.clear(trader);
        TraderCampData reloaded = TraderCampData.load(data.save(new CompoundTag()));
        assertFalse(reloaded.belongsTo(trader));
    }
}

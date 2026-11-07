package com.bettercontent.spiritcommerce.resident;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class ResidentNavigationTest {
    @Test void villagersKeepNativeShelterNavigation() {
        assertFalse(ResidentService.gatherNavigationAllowed(true, false));
        assertTrue(ResidentService.gatherNavigationAllowed(true, true));
        assertTrue(ResidentService.gatherNavigationAllowed(false, false));
        assertFalse(ResidentService.shelterNavigationAllowed(true, true, false, true));
        assertTrue(ResidentService.shelterNavigationAllowed(false, true, false, true));
        assertFalse(ResidentService.shelterNavigationAllowed(false, false, false, true));
        assertFalse(ResidentService.shelterNavigationAllowed(false, true, true, true));
        assertFalse(ResidentService.shelterNavigationAllowed(false, true, false, false));
    }

    @Test void failedRoutesBackOffOnlyForTheFailedDestination() {
        BlockPos unreachable = new BlockPos(1, 2, 3);
        var failure = new ResidentService.FailedRoute(unreachable, 700);
        assertTrue(failure.blocks(unreachable, 100));
        assertTrue(failure.blocks(unreachable, 699));
        assertFalse(failure.blocks(unreachable, 700));
        assertFalse(failure.blocks(unreachable.east(), 100));
    }
}

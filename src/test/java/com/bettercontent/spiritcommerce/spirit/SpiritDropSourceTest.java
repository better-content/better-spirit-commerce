package com.bettercontent.spiritcommerce.spirit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SpiritDropSourceTest {
    @Test
    void deathPathUsesMalumResultsAtTheVictimWithoutADeferredDelivery() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bettercontent/spiritcommerce/spirit/SpiritAcquisition.java"));
        assertTrue(source.contains("SpiritHarvestHandler.getSpawnedSpirits(victim, recipient, ItemStack.EMPTY)"));
        assertTrue(source.contains("releaseGroupedSpirits(drops, victim, recipient)"));
        assertFalse(source.contains("onServerTick"));
    }
}

package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class FontTradeAffinityTest {
    @Test void eachSpiritMarketSellsOnlyDestinationsWithMatchingSalience() {
        Map<SpiritKind, Set<String>> expected = Map.of(
                SpiritKind.POWER, Set.of("nether", "ratlantis"),
                SpiritKind.MOTION, Set.of("aether", "bumblezone"),
                SpiritKind.ENDURANCE, Set.of("ratlantis", "bumblezone"),
                SpiritKind.RENEWAL, Set.of("aether", "bumblezone"),
                SpiritKind.CONTROL, Set.of("nether"));
        for (SpiritKind kind : SpiritKind.values()) assertEquals(expected.get(kind), FontTradeAffinity.destinations(kind));
    }
}

package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import java.util.Set;

/** Surveyed Font destinations whose salience aspects fit each spirit market. */
final class FontTradeAffinity {
    private FontTradeAffinity() {}

    static Set<String> destinations(final SpiritKind kind) {
        return switch (kind) {
            case POWER -> Set.of("nether", "ratlantis"); // previous Infernal + Arcane
            case MOTION -> Set.of("aether", "bumblezone"); // previous Aerial + Tempo
            case ENDURANCE -> Set.of("ratlantis", "bumblezone"); // previous Aqueous + Earthen
            case RENEWAL -> Set.of("aether", "bumblezone"); // previous Sacred
            case CONTROL -> Set.of("nether"); // previous Wicked
        };
    }
}

package com.bettercontent.spiritcommerce.spirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SpiritTypeCoverageTest {
    @Test
    void eachOrdinaryMalumSpiritHasOneFixedCommerceIdentity() {
        Map<String, CurrencyIdentity> types = Map.of(
                "infernal", CurrencyIdentity.IMPACT,
                "arcane", CurrencyIdentity.WORK,
                "aerial", CurrencyIdentity.MOBILITY,
                "aqueous", CurrencyIdentity.ENDURANCE,
                "earthen", CurrencyIdentity.ROBUSTNESS,
                "sacred", CurrencyIdentity.RENEWAL,
                "wicked", CurrencyIdentity.CONTROL);
        types.forEach((nativeType, identity) -> {
            assertEquals(identity, CurrencyIdentity.fromLegacyNativeSpirit(
                    new ResourceLocation("malum", nativeType + "_spirit")));
            assertEquals(identity, CurrencyIdentity.fromItemId(identity.itemId()));
        });
        assertNull(CurrencyIdentity.fromLegacyNativeSpirit(new ResourceLocation("malum:eldritch_spirit")));
        assertNull(CurrencyIdentity.fromLegacyNativeSpirit(new ResourceLocation("malum:umbral_spirit")));
        assertEquals(5, CurrencyIdentity.values().length);
        assertNull(CurrencyIdentity.fromNativeName("tempo"));
    }
}

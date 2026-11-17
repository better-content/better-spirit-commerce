package com.bettercontent.spiritcommerce.ops;

import static org.junit.jupiter.api.Assertions.*;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ObservationalSpiritExportTest {
    @Test void exportIsOptInAndPermissionGated() {
        var released = Map.of(CurrencyIdentity.IMPACT, 4);
        assertEquals("", ObservationalSpiritExport.csv(false, 4, released, List.of()));
        assertEquals("", ObservationalSpiritExport.json(true, 1, released, List.of()));
    }

    @Test void exportReportsCompletedPhysicalReleases() {
        String json = ObservationalSpiritExport.json(true, 2,
                Map.of(CurrencyIdentity.IMPACT, 4, CurrencyIdentity.TEMPO, 3), List.of());
        assertTrue(json.contains("\"released\":{\"power\":4,\"motion\":3}"));
        assertFalse(json.contains("\"pending\""));
    }

    @Test void exchangesContainOnlyExplicitSuccessfulObservations() {
        var exchange = new ObservationalSpiritExport.Exchange(CurrencyIdentity.WORK, 2, "minecraft:villager");
        String csv = ObservationalSpiritExport.csv(true, 2, Map.of(), List.of(exchange));
        assertTrue(csv.contains("exchange,power,2,minecraft:villager"));
        assertFalse(csv.contains("motive"));
        assertFalse(csv.contains("private"));
    }

    @Test void nonemptyRegionalAndPurchaseDataIsValidJson() {
        String json = ObservationalSpiritExport.json(true, 2, Map.of(), List.of(), 0L,
                Map.of("minecraft:plains", Map.of(CurrencyIdentity.IMPACT, 4)),
                Map.of(CurrencyIdentity.WORK, 2));
        var parsed = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        assertEquals(4, parsed.getAsJsonObject("regionalMix").getAsJsonObject("minecraft:plains").get("power").getAsInt());
        assertEquals(2, parsed.getAsJsonObject("purchases").get("power").getAsInt());
    }
}

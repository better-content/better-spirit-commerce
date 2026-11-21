package com.bettercontent.spiritcommerce.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SpiritWorkstationRecipeTest {
    @Test void everyProfessionHasMatchingStationRecipe() throws Exception {
        Map<SpiritKind, Station> stations = Map.of(
                SpiritKind.POWER, new Station("infernal_kiln", "minecraft:blackstone"),
                SpiritKind.MOTION, new Station("aerial_waypost", "minecraft:bamboo_planks"),
                SpiritKind.ENDURANCE, new Station("aqueous_font", "minecraft:prismarine"),
                SpiritKind.RENEWAL, new Station("sacred_reliquary", "minecraft:oak_planks"),
                SpiritKind.CONTROL, new Station("wicked_effigy", "minecraft:dark_oak_planks"));
        assertEquals(SpiritKind.values().length, stations.size());
        for (var entry : stations.entrySet()) {
            String path = "/data/better_spirit_commerce/recipes/" + entry.getValue().id() + ".json";
            try (var resource = getClass().getResourceAsStream(path)) {
                assertNotNull(resource, "Missing spirit villager job-site recipe: " + path);
                var recipe = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
                assertEquals("better_spirit_commerce:" + entry.getValue().id(),
                        recipe.getAsJsonObject("result").get("item").getAsString());
                assertEquals(entry.getValue().material(),
                        recipe.getAsJsonObject("key").getAsJsonObject("P").get("item").getAsString());
                assertEquals("better_spirit_commerce:" + entry.getKey().id() + "_spirit",
                        recipe.getAsJsonObject("key").getAsJsonObject("S").get("item").getAsString());
            }
        }
    }

    private record Station(String id, String material) {}
}

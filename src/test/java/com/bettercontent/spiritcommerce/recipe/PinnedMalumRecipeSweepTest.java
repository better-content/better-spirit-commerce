package com.bettercontent.spiritcommerce.recipe;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Reads the pinned native Malum JAR on the classpath, not a generated fixture or a running world. UNRUN. */
final class PinnedMalumRecipeSweepTest {
    private static final String PREFIX = "data/malum/recipes/";
    private static final Set<String> NATIVE_SERIALIZERS = Set.of(
            "malum:spirit_infusion", "malum:spirit_repair", "malum:spirit_focusing");
    private static final Set<String> RETIRED = Set.of(
            "malum:crude_scythe", "malum:soul_stained_steel_axe", "malum:soul_stained_steel_hoe",
            "malum:soul_stained_steel_pickaxe", "malum:soul_stained_steel_shovel",
            "malum:soul_stained_steel_sword", "malum:malum/soul_stained_steel_knife",
            "malum:spirit_infusion/soul_stained_steel_scythe",
            "malum:spirit_infusion/mnemonic_hex_staff",
            "malum:spirit_infusion/staff_of_the_auric_flame");
    private static final Map<String, String> ORDINARY = Map.of(
            "infernal", "infernal", "arcane", "infernal", "aerial", "aerial",
            "aqueous", "aqueous", "earthen", "aqueous", "sacred", "sacred", "wicked", "wicked");
    private static final Map<String, String> PHYSICAL = Map.of(
            "infernal", "power", "arcane", "power", "aerial", "motion",
            "aqueous", "endurance", "earthen", "endurance", "sacred", "renewal", "wicked", "control");

    @Test void allPinnedNativeSpiritListsKeepTheirIdsExactOutputsOtherCostsAndSummedRequirements() throws Exception {
        int inputRecipes = 0, liveRecipes = 0, intentionallyRetired = 0;
        try (JarFile jar = nativeJar()) {
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (!entry.getName().startsWith(PREFIX) || !entry.getName().endsWith(".json")) continue;
                JsonObject raw = JsonParser.parseString(new String(jar.getInputStream(entry).readAllBytes(),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                if (!raw.has("spirits") || !raw.get("spirits").isJsonArray()) continue;
                inputRecipes++;
                String name = entry.getName().substring(PREFIX.length(), entry.getName().length() - 5);
                ResourceLocation id = new ResourceLocation("malum", name);
                assertTrue(NATIVE_SERIALIZERS.contains(raw.get("type").getAsString()), id.toString());
                var result = CoinRecipeFilter.filter(Map.of(id, raw));
                if (RETIRED.contains(id.toString())) {
                    intentionallyRetired++;
                    assertFalse(result.containsKey(id), "Previously retired equipment must stay excluded: " + id);
                    continue;
                }
                liveRecipes++;
                assertEquals(Set.of(id), result.keySet(), "Native recipe ID and reachability: " + id);
                JsonObject mapped = result.get(id).getAsJsonObject();
                for (String key : Set.of("output", "result", "results")) {
                    assertEquals(raw.get(key), mapped.get(key), "Exact native output and quantity: " + id);
                }
                JsonObject ordinaryFields = raw.deepCopy();
                JsonObject groupedFields = mapped.deepCopy();
                ordinaryFields.remove("spirits");
                groupedFields.remove("spirits");
                assertEquals(ordinaryFields, groupedFields, "Nonspirit inputs, other costs and recipe fields: " + id);
                Map<String, Integer> expected = new LinkedHashMap<>();
                for (JsonElement spirit : raw.getAsJsonArray("spirits")) {
                    JsonObject row = spirit.getAsJsonObject();
                    String before = row.get("type").getAsString();
                    expected.merge(ORDINARY.getOrDefault(before, before), row.get("count").getAsInt(), Math::addExact);
                }
                Map<String, Integer> actual = new HashMap<>();
                for (JsonElement spirit : mapped.getAsJsonArray("spirits")) {
                    JsonObject row = spirit.getAsJsonObject();
                    assertNull(actual.put(row.get("type").getAsString(), row.get("count").getAsInt()),
                            "No duplicate physical requirement for " + id);
                }
                assertEquals(expected, actual, "Convergent type counts and specialists: " + id);
            }
        }
        assertEquals(138, inputRecipes, "Pinned native spirit-list inventory drift requires review");
        assertEquals(3, intentionallyRetired, "Three spirit-list equipment IDs retired by prior policy");
        assertEquals(135, liveRecipes, "All other native spirit-list recipes must remain identified and loaded");
    }

    @Test void allTenPreviouslyRetiredNativeEquipmentRecipesRemainExplicitlyExcluded() throws Exception {
        try (JarFile jar = nativeJar()) {
            for (String name : RETIRED) {
                ResourceLocation id = new ResourceLocation(name);
                var resource = jar.getJarEntry(PREFIX + id.getPath() + ".json");
                assertNotNull(resource, "Pinned retired equipment ID must still exist for audit: " + id);
                JsonElement source = JsonParser.parseString(new String(jar.getInputStream(resource).readAllBytes(),
                        StandardCharsets.UTF_8));
                assertFalse(CoinRecipeFilter.filter(Map.of(id, source)).containsKey(id),
                        "Do not accidentally reinstate a retired native equipment recipe: " + id);
            }
        }
    }

    @Test void allTwentyTwoOrdinaryRuneItemsRetainExactRawCountsAndEverythingExceptTheirItemId() throws Exception {
        int ordinary = 0, specialist = 0;
        Set<ResourceLocation> visited = new HashSet<>();
        try (JarFile jar = nativeJar()) {
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (!entry.getName().startsWith(PREFIX + "runeworking/") || !entry.getName().endsWith(".json")) continue;
                JsonObject raw = JsonParser.parseString(new String(jar.getInputStream(entry).readAllBytes(),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                if (!raw.has("secondaryInput") || !raw.get("secondaryInput").isJsonObject()) continue;
                JsonObject input = raw.getAsJsonObject("secondaryInput");
                if (!input.has("item") || !input.get("item").getAsString().matches("malum:[a-z]+_spirit")) continue;
                String name = entry.getName().substring(PREFIX.length(), entry.getName().length() - 5);
                ResourceLocation id = new ResourceLocation("malum", name);
                assertTrue(visited.add(id), "No duplicate native rune resource: " + id);
                JsonObject mapped = CoinRecipeFilter.filter(Map.of(id, raw)).get(id).getAsJsonObject();
                String type = input.get("item").getAsString().substring(6).replace("_spirit", "");
                String replacement = PHYSICAL.containsKey(type)
                        ? "better_spirit_commerce:" + PHYSICAL.get(type) + "_spirit" : input.get("item").getAsString();
                JsonObject expected = raw.deepCopy();
                expected.getAsJsonObject("secondaryInput").addProperty("item", replacement);
                assertEquals(expected, mapped, "Native rune ID, output, tablet, all costs and raw count: " + id);
                assertTrue(input.get("count").getAsInt() == 16 || input.get("count").getAsInt() == 32,
                        "Pinned 16/32 raw rune amount: " + id);
                if (PHYSICAL.containsKey(type)) ordinary++; else {
                    assertEquals("eldritch", type, "Specialist inputs retain their item: " + id);
                    specialist++;
                }
            }
        }
        assertEquals(22, ordinary, "Every ordinary raw-item rune must become a real core reagent");
        assertEquals(2, specialist, "Two Eldritch raw-item runes stay specialist");
    }

    private static JarFile nativeJar() throws Exception {
        var marker = CoinRecipeFilter.class.getClassLoader().getResource(
                "data/malum/recipes/runeworking/rune_of_fervor.json");
        assertNotNull(marker, "Pinned native Malum recipe resource missing");
        if (marker.openConnection() instanceof JarURLConnection jar) {
            return new JarFile(Path.of(jar.getJarFileURL().toURI()).toFile());
        }
        fail("Need Malum's runtime classpath JAR URL for complete pinned-resource sweep, got " + marker);
        return null;
    }
}

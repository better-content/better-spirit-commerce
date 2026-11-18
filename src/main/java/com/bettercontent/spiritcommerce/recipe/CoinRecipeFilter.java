package com.bettercontent.spiritcommerce.recipe;

import com.bettercontent.spiritcommerce.config.EconomyPolicy;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Removes retired coin and superseded Malum equipment recipes at the reload boundary. */
public final class CoinRecipeFilter {
    private static final java.util.Set<ResourceLocation> RETIRED_RECIPES = java.util.Set.of(
            id("malum:crude_scythe"), id("malum:soul_stained_steel_axe"),
            id("malum:soul_stained_steel_hoe"), id("malum:soul_stained_steel_pickaxe"),
            id("malum:soul_stained_steel_shovel"), id("malum:soul_stained_steel_sword"),
            id("malum:malum/soul_stained_steel_knife"), id("malum:spirit_infusion/soul_stained_steel_scythe"),
            id("malum:spirit_infusion/mnemonic_hex_staff"), id("malum:spirit_infusion/staff_of_the_auric_flame"));

    private CoinRecipeFilter() {}

    public static Map<ResourceLocation, JsonElement> filter(Map<ResourceLocation, JsonElement> recipes) {
        Map<ResourceLocation, JsonElement> filtered = new LinkedHashMap<>();
        recipes.forEach((id, json) -> {
            if (RETIRED_RECIPES.contains(id)) return;
            // Eight old economy->native identity adapters are obsolete. Rewriting
            // them would create self-to-self focusing recipes that waste durability.
            if ("better_spirit_commerce".equals(id.getNamespace())
                    && id.getPath().startsWith("spirit_refinement/")) return;
            JsonElement replacement = canonicalizeSerialized(json);
            if (!hasCoinOutput(replacement)) filtered.put(id, replacement);
        });
        return filtered;
    }

    /** Idempotent final-JSON boundary shared by vanilla reload and KubeJS post-script
     * serialization. KubeJS may add ordinary Malum items to any recipe serializer.
     */
    public static JsonElement canonicalizeSerialized(JsonElement json) {
        return mergeSpiritRequirements(replaceOrdinarySpirits(json));
    }

    public static JsonObject canonicalizeSerialized(JsonObject json) {
        return canonicalizeSerialized((JsonElement) json).getAsJsonObject();
    }

    static boolean hasCoinOutput(JsonElement recipe) {
        if (!recipe.isJsonObject()) return false;
        JsonObject root = recipe.getAsJsonObject();
        return matchesOutput(root.get("result")) || matchesOutput(root.get("output")) || matchesOutput(root.get("results"));
    }

    private static boolean matchesOutput(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonPrimitive()) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            return id != null && EconomyPolicy.isRetired(id);
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) if (matchesOutput(child)) return true;
            return false;
        }
        JsonObject object = element.getAsJsonObject();
        for (String key : List.of("item", "id", "result", "output")) {
            if (matchesOutput(object.get(key))) return true;
        }
        return false;
    }

    private static ResourceLocation id(String value) { return new ResourceLocation(value); }

    /** Merge convergent Malum requirements before native deserialization, including an
     * inner recipe inside a Forge conditional wrapper. Never touch an unrelated mod's
     * generic `spirits` field. The separate raw-item walk retains rune counts 16/32.
     */
    static JsonElement mergeSpiritRequirements(JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return element;
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            element.getAsJsonArray().forEach(child -> result.add(mergeSpiritRequirements(child)));
            return result;
        }
        JsonObject object = new JsonObject();
        element.getAsJsonObject().entrySet().forEach(entry ->
                object.add(entry.getKey(), mergeSpiritRequirements(entry.getValue())));
        if (!isNativeSpiritRecipe(object) || !object.has("spirits") || !object.get("spirits").isJsonArray()) {
            return object;
        }
        JsonArray inputs = object.getAsJsonArray("spirits");
        Map<String, JsonObject> totals = new LinkedHashMap<>();
        JsonArray result = new JsonArray();
        for (JsonElement input : inputs) {
            if (!input.isJsonObject() || !input.getAsJsonObject().has("type")) {
                result.add(input.deepCopy());
                continue;
            }
            JsonObject spirit = input.getAsJsonObject();
            String nativeName = spirit.get("type").getAsString();
            // Malum's omitted count means one spirit; materialize that default before
            // merging so single-count recipes cannot escape the grouping boundary.
            int count = spirit.has("count") ? spirit.get("count").getAsInt() : 1;
            String grouped = switch (nativeName) {
                case "infernal", "arcane" -> "infernal";
                case "aerial" -> "aerial";
                case "aqueous", "earthen" -> "aqueous";
                case "sacred" -> "sacred";
                case "wicked" -> "wicked";
                default -> nativeName; // Eldritch/Umbral retain specialist identity.
            };
            JsonObject earlier = totals.get(grouped);
            if (earlier == null) {
                JsonObject canonical = spirit.deepCopy();
                canonical.addProperty("type", grouped);
                canonical.addProperty("count", count);
                totals.put(grouped, canonical);
                result.add(canonical);
            } else {
                earlier.addProperty("count", Math.addExact(earlier.get("count").getAsInt(), count));
            }
        }
        object.add("spirits", result);
        return object;
    }

    private static boolean isNativeSpiritRecipe(JsonObject object) {
        if (!object.has("type") || !object.get("type").isJsonPrimitive()) return false;
        return switch (object.get("type").getAsString()) {
            case "malum:spirit_infusion", "malum:spirit_repair", "malum:spirit_focusing" -> true;
            default -> false;
        };
    }

    /** Changes exact ordinary Malum item IDs in loaded recipes, including third-party recipes. */
    static JsonElement replaceOrdinarySpirits(JsonElement element) {
        if (element == null || element.isJsonNull()) return element;
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) {
                ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
                CurrencyIdentity identity = CurrencyIdentity.fromLegacyNativeSpirit(id);
                if (identity != null) return new com.google.gson.JsonPrimitive(identity.itemId().toString());
            }
            return element.deepCopy();
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            element.getAsJsonArray().forEach(child -> result.add(replaceOrdinarySpirits(child)));
            return result;
        }
        JsonObject result = new JsonObject();
        element.getAsJsonObject().entrySet().forEach(entry ->
                result.add(entry.getKey(), replaceOrdinarySpirits(entry.getValue())));
        return result;
    }
}

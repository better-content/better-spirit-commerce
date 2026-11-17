package com.bettercontent.spiritcommerce.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CoinRecipeFilterTest {
    @Test void identifiesPrimitiveAndStructuredCoinOutputsOnly() {
        assertTrue(CoinRecipeFilter.hasCoinOutput(JsonParser.parseString("{\"result\":\"createdeco:copper_coin\"}")));
        assertTrue(CoinRecipeFilter.hasCoinOutput(JsonParser.parseString("{\"results\":[{\"item\":\"createdeco:gold_coinstack\"}]}")));
        assertFalse(CoinRecipeFilter.hasCoinOutput(JsonParser.parseString("{\"output\":{\"item\":\"malum:arcane_spirit\"}}")));
        assertFalse(CoinRecipeFilter.hasCoinOutput(JsonParser.parseString(
                "{\"ingredient\":{\"item\":\"createdeco:copper_coin\"},\"result\":{\"item\":\"minecraft:stick\"}}")));
    }

    @Test void rewritesMalumRecipeInputsAndOutputsButKeepsExoticSpirits() {
        var recipe = JsonParser.parseString("{\"secondaryInput\":{\"item\":\"malum:arcane_spirit\"},"
                + "\"output\":{\"item\":\"malum:infernal_spirit\"},"
                + "\"spirits\":[\"malum:eldritch_spirit\"]}");
        var result = CoinRecipeFilter.filter(Map.of(new ResourceLocation("malum", "runeworking/test"), recipe))
                .get(new ResourceLocation("malum", "runeworking/test")).getAsJsonObject();
        assertTrue(result.getAsJsonObject("secondaryInput").get("item").getAsString()
                .equals("better_spirit_commerce:power_spirit"));
        assertTrue(result.getAsJsonObject("output").get("item").getAsString()
                .equals("better_spirit_commerce:power_spirit"));
        assertTrue(result.toString().contains("malum:eldritch_spirit"));
    }

    @Test void obsoleteRefinementAdaptersCannotTurnIntoSelfToSelfCostRecipes() {
        var id = new ResourceLocation("better_spirit_commerce", "spirit_refinement/work");
        var recipe = JsonParser.parseString("{\"input\":{\"item\":\"better_spirit_commerce:work_spirit\"},"
                + "\"output\":{\"item\":\"malum:arcane_spirit\"}}");
        assertFalse(CoinRecipeFilter.filter(Map.of(id, recipe)).containsKey(id));
    }

    @Test void nativeArrayMergedCostsAndRuneRawItemCountsAreBothPreserved() {
        var id = new ResourceLocation("malum", "spirit_infusion/merged_test");
        var parsed = JsonParser.parseString("{\"type\":\"malum:spirit_infusion\",\"output\":{\"item\":\"minecraft:stick\"},"
                + "\"secondaryInput\":{\"item\":\"malum:earthen_spirit\",\"count\":32},"
                + "\"spirits\":[{\"type\":\"arcane\",\"count\":2},{\"type\":\"infernal\",\"count\":3},"
                + "{\"type\":\"earthen\",\"count\":4},{\"type\":\"aqueous\",\"count\":5},"
                + "{\"type\":\"eldritch\",\"count\":6}]}");
        var merged = CoinRecipeFilter.filter(Map.of(id, parsed)).get(id).getAsJsonObject();
        assertTrue(merged.getAsJsonObject("secondaryInput").get("item").getAsString()
                .equals("better_spirit_commerce:endurance_spirit"));
        assertTrue(merged.getAsJsonObject("secondaryInput").get("count").getAsInt() == 32);
        var rows = merged.getAsJsonArray("spirits");
        assertTrue(rows.size() == 3);
        assertTrue(rows.get(0).getAsJsonObject().get("type").getAsString().equals("infernal"));
        assertTrue(rows.get(0).getAsJsonObject().get("count").getAsInt() == 5);
        assertTrue(rows.get(1).getAsJsonObject().get("type").getAsString().equals("aqueous"));
        assertTrue(rows.get(1).getAsJsonObject().get("count").getAsInt() == 9);
        assertTrue(rows.get(2).getAsJsonObject().get("type").getAsString().equals("eldritch"));
        assertTrue(rows.get(2).getAsJsonObject().get("count").getAsInt() == 6);
        assertTrue(merged.getAsJsonObject("output").get("item").getAsString().equals("minecraft:stick"));
    }

    @Test void postScriptFinalJsonIncludesNewNonMalumIngredientsAndStillMergesNativeCostsOnce() {
        var scriptedCauldron = JsonParser.parseString("{\"type\":\"hexerei:mixing_cauldron\","
                + "\"ingredients\":[{\"item\":\"malum:sacred_spirit\"},{\"item\":\"malum:wicked_spirit\"}],"
                + "\"output\":{\"item\":\"minecraft:stick\",\"count\":2}}")
                .getAsJsonObject();
        var added = CoinRecipeFilter.canonicalizeSerialized(scriptedCauldron);
        assertEquals("better_spirit_commerce:renewal_spirit", added.getAsJsonArray("ingredients")
                .get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("better_spirit_commerce:control_spirit", added.getAsJsonArray("ingredients")
                .get(1).getAsJsonObject().get("item").getAsString());
        assertEquals(scriptedCauldron.get("output"), added.get("output"));
        assertEquals(added, CoinRecipeFilter.canonicalizeSerialized(added));

        var afterScript = JsonParser.parseString("{\"type\":\"malum:spirit_focusing\",\"time\":100,"
                + "\"durabilityCost\":4,\"input\":{\"item\":\"minecraft:stone\"},"
                + "\"output\":{\"item\":\"minecraft:diamond\",\"count\":3},"
                + "\"spirits\":[{\"type\":\"arcane\",\"count\":2},"
                + "{\"type\":\"infernal\",\"count\":3}]}").getAsJsonObject();
        var finalRecipe = CoinRecipeFilter.canonicalizeSerialized(afterScript);
        assertEquals(1, finalRecipe.getAsJsonArray("spirits").size());
        assertEquals("infernal", finalRecipe.getAsJsonArray("spirits").get(0)
                .getAsJsonObject().get("type").getAsString());
        assertEquals(5, finalRecipe.getAsJsonArray("spirits").get(0)
                .getAsJsonObject().get("count").getAsInt());
        assertEquals(afterScript.get("time"), finalRecipe.get("time"));
        assertEquals(afterScript.get("durabilityCost"), finalRecipe.get("durabilityCost"));
        assertEquals(afterScript.get("output"), finalRecipe.get("output"));
        assertEquals(finalRecipe, CoinRecipeFilter.canonicalizeSerialized(finalRecipe));
    }

    @Test void nativeFinalKubeHookAndIncomingPostHookArePresentWithoutMakingTheScriptAnInputProvider() throws Exception {
        String finalSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/bettercontent/spiritcommerce/mixin/KubeRecipeFinalMixin.java"));
        String incomingSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/bettercontent/spiritcommerce/mixin/KubeRecipesEventMixin.java"));
        String config = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/better_spirit_commerce.mixins.json"));
        assertTrue(finalSource.contains("method = \"createRecipe\"")
                && finalSource.contains("target = \"Ldev/latvian/mods/kubejs/recipe/RecipeJS;getOrCreateId()")
                && finalSource.contains("ordinal = 0, shift = At.Shift.AFTER")
                && finalSource.contains("ordinal = 1, shift = At.Shift.AFTER")
                && finalSource.contains("json = CoinRecipeFilter.canonicalizeSerialized(json)"),
                "Native final hook must preserve Kube generated IDs then normalize both serializer branches");
        assertTrue(incomingSource.contains("method = \"post\"")
                && incomingSource.contains("argsOnly = true")
                && incomingSource.contains("CoinRecipeFilter.filter(recipes)"));
        assertTrue(config.contains("\"KubeRecipeFinalMixin\"")
                && config.contains("\"KubeRecipesEventMixin\""));
    }

    @Test void nestedNativeRecipeRequirementsMergeWithoutTouchingOtherSerializersOrChangingOutput() {
        var id = new ResourceLocation("malum", "conditional_grouping");
        var original = JsonParser.parseString("{\"type\":\"forge:conditional\",\"recipes\":["
                + "{\"conditions\":[],\"recipe\":{\"type\":\"malum:spirit_infusion\","
                + "\"input\":{\"item\":\"minecraft:iron_ingot\"},\"spirits\":["
                + "{\"type\":\"arcane\",\"count\":4},{\"type\":\"infernal\",\"count\":6}],"
                + "\"output\":{\"item\":\"minecraft:diamond\",\"count\":3}}},"
                + "{\"conditions\":[],\"recipe\":{\"type\":\"example:other\",\"spirits\":["
                + "{\"type\":\"arcane\",\"count\":4},{\"type\":\"infernal\",\"count\":6}],"
                + "\"output\":{\"item\":\"minecraft:gold_ingot\"}}}]}");
        var once = CoinRecipeFilter.filter(Map.of(id, original));
        var twice = CoinRecipeFilter.filter(once);
        assertEquals(once, twice, "Reapplying the pre-deserialization filter must be idempotent");
        var recipes = once.get(id).getAsJsonObject().getAsJsonArray("recipes");
        var nativeRecipe = recipes.get(0).getAsJsonObject().getAsJsonObject("recipe");
        assertEquals(1, nativeRecipe.getAsJsonArray("spirits").size());
        assertEquals("infernal", nativeRecipe.getAsJsonArray("spirits").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(10, nativeRecipe.getAsJsonArray("spirits").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(original.getAsJsonObject().getAsJsonArray("recipes").get(0).getAsJsonObject()
                .getAsJsonObject("recipe").getAsJsonObject("output"), nativeRecipe.getAsJsonObject("output"));
        assertEquals(original.getAsJsonObject().getAsJsonArray("recipes").get(0).getAsJsonObject()
                .getAsJsonObject("recipe").getAsJsonObject("input"), nativeRecipe.getAsJsonObject("input"));
        assertEquals(original.getAsJsonObject().getAsJsonArray("recipes").get(1).getAsJsonObject()
                .getAsJsonObject("recipe").getAsJsonArray("spirits"), recipes.get(1).getAsJsonObject()
                .getAsJsonObject("recipe").getAsJsonArray("spirits"));
    }
}

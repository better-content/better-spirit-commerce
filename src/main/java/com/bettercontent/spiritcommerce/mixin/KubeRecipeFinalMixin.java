package com.bettercontent.spiritcommerce.mixin;

import com.bettercontent.spiritcommerce.recipe.CoinRecipeFilter;
import com.google.gson.JsonObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pinned KubeJS RecipeJS.createRecipe() serializes additions/edits and then
 * calls RecipePlatformHelper.fromJson(serializer,id,json), or a stage serializer.
 * Intercept after serialize() AND the first getOrCreateId(), preserving generated
 * IDs before changing JSON, but still before stage/native deserialization. The
 * second getOrCreateId() covers the unchanged/originalRecipe-null fallback;
 * unchanged originals normally come from filtered RecipesEventJS.post(Map).
 */
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.recipe.RecipeJS", remap = false)
abstract class KubeRecipeFinalMixin {
    @Shadow public JsonObject json;

    @Inject(method = "createRecipe", at = @At(value = "INVOKE",
            target = "Ldev/latvian/mods/kubejs/recipe/RecipeJS;getOrCreateId()Lnet/minecraft/resources/ResourceLocation;",
            ordinal = 0, shift = At.Shift.AFTER), remap = false)
    private void betterContentEconomy$canonicalizeAfterGeneratedId(CallbackInfoReturnable<?> callback) {
        json = CoinRecipeFilter.canonicalizeSerialized(json);
    }

    @Inject(method = "createRecipe", at = @At(value = "INVOKE",
            target = "Ldev/latvian/mods/kubejs/recipe/RecipeJS;getOrCreateId()Lnet/minecraft/resources/ResourceLocation;",
            ordinal = 1, shift = At.Shift.AFTER), remap = false)
    private void betterContentEconomy$canonicalizeFallbackAfterId(CallbackInfoReturnable<?> callback) {
        json = CoinRecipeFilter.canonicalizeSerialized(json);
    }
}

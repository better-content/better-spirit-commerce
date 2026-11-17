package com.bettercontent.spiritcommerce.mixin;

import com.bettercontent.spiritcommerce.recipe.CoinRecipeFilter;
import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** KubeJS 2001.6.5 build 26 cancels vanilla RecipeManager.apply at HEAD and
 * deserializes its own map. Filter that incoming map before KubeJS reads it;
 * the separate final-JSON hook also handles scripted additions/edits.
 */
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.recipe.RecipesEventJS", remap = false)
abstract class KubeRecipesEventMixin {
    @ModifyVariable(method = "post", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private Map<ResourceLocation, JsonElement> betterContentEconomy$filterIncomingRecipes(
            Map<ResourceLocation, JsonElement> recipes) {
        return CoinRecipeFilter.filter(recipes);
    }
}

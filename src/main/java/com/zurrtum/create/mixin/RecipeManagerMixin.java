package com.zurrtum.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.AllRecipeSets;
import com.zurrtum.create.content.kinetics.mixer.PotionRecipe;
import com.zurrtum.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.zurrtum.create.foundation.recipe.GeneratedRecipeLookup;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeManager.IngredientExtractor;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipePropertySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
    // Since 26.3 recipes come from the reloadable recipe registry; generated recipes are only added to the map.
    // They go through RecipeMap.create so other mods hooking it (Fabric API's recipe sync) see the full map.
    @WrapOperation(method = "<init>(Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeMap;create(Lnet/minecraft/core/HolderLookup;)Lnet/minecraft/world/item/crafting/RecipeMap;"))
    private RecipeMap addGeneratedRecipes(
        HolderLookup<Recipe<?>> recipes,
        Operation<RecipeMap> original,
        @Local(argsOnly = true) HolderLookup.Provider registries
    ) {
        Map<Identifier, Recipe<?>> generated = new LinkedHashMap<>(SequencedAssemblyRecipe.GENERATE_RECIPES);
        SequencedAssemblyRecipe.GENERATE_RECIPES.clear();
        PotionRecipe.register(generated, recipes, registries);
        if (generated.isEmpty()) {
            return original.call(recipes);
        }
        return original.call(GeneratedRecipeLookup.with(recipes, generated));
    }

    @WrapOperation(method = "finalizeRecipeLoading(Lnet/minecraft/world/flag/FeatureFlagSet;)V", at = @At(value = "INVOKE", target = "Ljava/util/Set;stream()Ljava/util/stream/Stream;"))
    public Stream<Entry<ResourceKey<RecipePropertySet>, IngredientExtractor>> registerRecipeSet(
        Set<Entry<ResourceKey<RecipePropertySet>, IngredientExtractor>> instance,
        Operation<Stream<Entry<ResourceKey<RecipePropertySet>, IngredientExtractor>>> original
    ) {
        return Stream.concat(original.call(instance), AllRecipeSets.ALL.entrySet().stream());
    }
}

package com.zurrtum.create.foundation.recipe;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Recipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The recipe registry lookup plus the recipes Create generates while loading (sequenced assembly steps, potion
 * mixing), so they can be passed to {@code RecipeMap.create} like registry recipes.
 */
public final class GeneratedRecipeLookup implements HolderLookup<Recipe<?>> {
    private final HolderLookup<Recipe<?>> recipes;
    private final Map<ResourceKey<Recipe<?>>, Holder.Reference<Recipe<?>>> generated;

    private GeneratedRecipeLookup(HolderLookup<Recipe<?>> recipes, Map<ResourceKey<Recipe<?>>, Holder.Reference<Recipe<?>>> generated) {
        this.recipes = recipes;
        this.generated = generated;
    }

    /**
     * Generated recipes whose id is already taken by a registry recipe are skipped.
     */
    public static HolderLookup<Recipe<?>> with(HolderLookup<Recipe<?>> recipes, Map<Identifier, Recipe<?>> generated) {
        Map<ResourceKey<Recipe<?>>, Holder.Reference<Recipe<?>>> holders = new LinkedHashMap<>();
        generated.forEach((id, recipe) -> {
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
            if (recipes.get(key).isEmpty()) {
                holders.put(key, new GeneratedHolder(recipes, key, recipe));
            }
        });
        return new GeneratedRecipeLookup(recipes, holders);
    }

    @Override
    public Stream<Holder.Reference<Recipe<?>>> listElements() {
        return Stream.concat(recipes.listElements(), generated.values().stream());
    }

    @Override
    public Stream<HolderSet.Named<Recipe<?>>> listTags() {
        return recipes.listTags();
    }

    @Override
    public Optional<Holder.Reference<Recipe<?>>> get(ResourceKey<Recipe<?>> id) {
        Optional<Holder.Reference<Recipe<?>>> holder = recipes.get(id);
        return holder.isPresent() ? holder : Optional.ofNullable(generated.get(id));
    }

    @Override
    public Optional<HolderSet.Named<Recipe<?>>> get(TagKey<Recipe<?>> id) {
        return recipes.get(id);
    }

    private static final class GeneratedHolder extends Holder.Reference<Recipe<?>> {
        private GeneratedHolder(HolderLookup<Recipe<?>> owner, ResourceKey<Recipe<?>> key, Recipe<?> recipe) {
            super(Holder.Reference.Type.STAND_ALONE, owner, key, recipe);
        }
    }
}

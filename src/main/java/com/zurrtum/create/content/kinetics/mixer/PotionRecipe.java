package com.zurrtum.create.content.kinetics.mixer;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zurrtum.create.AllRecipeSerializers;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.content.fluids.potion.PotionFluidHandler;
import com.zurrtum.create.content.processing.basin.BasinInput;
import com.zurrtum.create.content.processing.basin.BasinRecipe;
import com.zurrtum.create.content.processing.recipe.HeatCondition;
import com.zurrtum.create.content.processing.recipe.SizedIngredient;
import com.zurrtum.create.foundation.blockEntity.behaviour.filtering.ServerFilteringBehaviour;
import com.zurrtum.create.foundation.fluid.FluidIngredient;
import com.zurrtum.create.infrastructure.component.BottleType;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.*;

import static com.zurrtum.create.Create.MOD_ID;

public record PotionRecipe(FluidStack result, FluidIngredient fluidIngredient,
                           Ingredient ingredient) implements BasinRecipe {
    public static final MapCodec<PotionRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec((RecordCodecBuilder.Instance<PotionRecipe> instance) -> instance.group(
        FluidStack.CODEC.fieldOf("result").forGetter(PotionRecipe::result),
        FluidIngredient.CODEC.fieldOf("fluid_ingredient").forGetter(PotionRecipe::fluidIngredient),
        Ingredient.CODEC.fieldOf("ingredient").forGetter(PotionRecipe::ingredient)
    ).apply(instance, PotionRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PotionRecipe> STREAM_CODEC = StreamCodec.composite(
        FluidStack.PACKET_CODEC,
        PotionRecipe::result,
        FluidIngredient.PACKET_CODEC,
        PotionRecipe::fluidIngredient,
        Ingredient.CONTENTS_STREAM_CODEC,
        PotionRecipe::ingredient,
        PotionRecipe::new
    );
    public static final RecipeSerializer<PotionRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);
    private static final List<Item> CONTAINERS = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);

    /**
     * Mirrors every brewing stand recipe as a basin mixing recipe. Since 26.3 brewing is data driven, so each
     * {@link BrewingRecipe} is evaluated against every potion in every bottle type instead of reading PotionBrewing.
     */
    public static void register(Map<Identifier, Recipe<?>> map, HolderLookup<Recipe<?>> recipes, HolderLookup.Provider registries) {
        List<Reference<Potion>> potions = registries.lookupOrThrow(Registries.POTION).listElements().toList();
        int recipeIndex = 0;
        for (Reference<Recipe<?>> holder : recipes.listElements().toList()) {
            if (!(holder.value() instanceof BrewingRecipe brewing) || brewing.getReagent().potions().isPresent()) {
                continue;
            }
            ItemStackTemplate output = brewing.getOutput();
            Item to = output.item().value();
            if (!CONTAINERS.contains(to)) {
                continue;
            }
            BottleType toBottleType = PotionFluidHandler.bottleTypeFromItem(to);
            PotionContents outputContents = output.components().get(DataComponentMap.EMPTY, DataComponents.POTION_CONTENTS);
            Ingredient ingredient = brewing.getReagent().ingredient();
            PotionIngredient brewingInput = brewing.getInput();
            for (Item from : CONTAINERS) {
                // Item components aren't bound while recipes load, so test the input without creating an ItemStack
                if (!brewingInput.ingredient().acceptsItem(from.builtInRegistryHolder())) {
                    continue;
                }
                BottleType fromBottleType = PotionFluidHandler.bottleTypeFromItem(from);
                for (Reference<Potion> potion : potions) {
                    PotionContents fromContents = new PotionContents(potion);
                    if (brewingInput.potions().isPresent() && !brewingInput.potions().get().matches(fromContents)) {
                        continue;
                    }
                    FluidIngredient fromFluid = PotionFluidHandler.getFluidIngredientFromPotion(fromContents, fromBottleType, 81000);
                    FluidStack toFluid = PotionFluidHandler.getFluidFromPotion(
                        outputContents != null ? outputContents : fromContents,
                        toBottleType,
                        81000
                    );
                    Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, "potion_mixing_vanilla_" + recipeIndex++);
                    map.put(id, new PotionRecipe(toFluid, fromFluid, ingredient));
                }
            }
        }
    }

    @Override
    public int getIngredientSize() {
        return 2;
    }

    @Override
    public List<SizedIngredient> ingredients() {
        return List.of(new SizedIngredient(ingredient, 1));
    }

    @Override
    public List<FluidIngredient> fluidIngredients() {
        return List.of(fluidIngredient);
    }

    @Override
    public HeatCondition heat() {
        return HeatCondition.HEATED;
    }

    @Override
    public boolean matches(BasinInput input, Level world) {
        if (!HeatCondition.HEATED.testBlazeBurner(input.heat())) {
            return false;
        }
        ServerFilteringBehaviour filter = input.filter();
        if (filter == null) {
            return false;
        }
        if (!filter.test(result)) {
            return false;
        }
        List<ItemStack> outputs = BasinRecipe.tryCraft(input, ingredient);
        if (outputs == null) {
            return false;
        }
        if (!BasinRecipe.matchFluidIngredient(input, fluidIngredient)) {
            return false;
        }
        return input.acceptOutputs(outputs, List.of(result), true);
    }

    @Override
    public boolean apply(BasinInput input) {
        if (!HeatCondition.HEATED.testBlazeBurner(input.heat())) {
            return false;
        }
        Deque<Runnable> changes = new ArrayDeque<>();
        List<ItemStack> outputs = BasinRecipe.prepareCraft(input, ingredient, changes);
        if (outputs == null) {
            return false;
        }
        if (!BasinRecipe.prepareFluidCraft(input, fluidIngredient, changes)) {
            return false;
        }
        List<FluidStack> fluids = List.of(result);
        if (!input.acceptOutputs(outputs, fluids, true)) {
            return false;
        }
        changes.forEach(Runnable::run);
        return input.acceptOutputs(outputs, fluids, false);
    }

    @Override
    public RecipeSerializer<PotionRecipe> getSerializer() {
        return AllRecipeSerializers.POTION;
    }

    @Override
    public RecipeType<PotionRecipe> getType() {
        return AllRecipeTypes.POTION;
    }
}
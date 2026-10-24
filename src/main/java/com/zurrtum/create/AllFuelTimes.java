package com.zurrtum.create;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static com.zurrtum.create.Create.MOD_ID;

public class AllFuelTimes {
    // Values live in data/create/context_int_provider/cooking, halved in smokers and blast furnaces like vanilla fuels
    public static final ResourceKey<ContextIntProvider> BLAZE_CAKE = key("cooking/time_blaze_cake");
    public static final ResourceKey<ContextIntProvider> CREATIVE_BLAZE_CAKE = key("cooking/time_creative_blaze_cake");
    public static final ResourceKey<ContextIntProvider> CARDBOARD = key("cooking/time_cardboard");
    public static final ResourceKey<ContextIntProvider> CARDBOARD_BLOCK = key("cooking/time_cardboard_block");

    // Fuel consumed outside of a furnace: no block means the vanilla fast_cooking condition is false
    private static final ContextKeySet FUEL_CONTEXT = new ContextKeySet.Builder().optional(LootContextParams.ORIGIN)
        .optional(LootContextParams.BLOCK_STATE).optional(LootContextParams.BLOCK_ENTITY).build();

    private static ResourceKey<ContextIntProvider> key(String path) {
        return ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, Identifier.fromNamespaceAndPath(MOD_ID, path));
    }

    /**
     * Furnace burn time of the stack, replacing {@code FuelValues#burnDuration} which was removed in 26.3.
     * Providers can only be resolved on the server, so the client gets a positive placeholder for non-constant fuel.
     */
    public static int getBurnDuration(Level level, ItemStack stack, @Nullable Vec3 origin, @Nullable BlockState state, @Nullable BlockEntity be) {
        CookingFuel fuel = stack.get(DataComponents.COOKING_FUEL);
        if (fuel == null) {
            return 0;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return fuel.burnTime() instanceof ResolvableInt.Constant(int value) ? value : 1;
        }
        LootParams params = new LootParams.Builder(serverLevel).withOptionalParameter(LootContextParams.ORIGIN, origin)
            .withOptionalParameter(LootContextParams.BLOCK_STATE, state).withOptionalParameter(LootContextParams.BLOCK_ENTITY, be)
            .create(FUEL_CONTEXT);
        LootContext context = new LootContext.Builder(params).create(Optional.empty());
        return fuel.burnTime().get(context, 0);
    }
}

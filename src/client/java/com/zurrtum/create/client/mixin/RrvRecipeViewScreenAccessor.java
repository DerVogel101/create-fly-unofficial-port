package com.zurrtum.create.client.mixin;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeViewScreen.class)
public interface RrvRecipeViewScreenAccessor {
    @Accessor(value = "hoveredSlot", remap = false)
    @Nullable Slot create$getHoveredSlot();
}

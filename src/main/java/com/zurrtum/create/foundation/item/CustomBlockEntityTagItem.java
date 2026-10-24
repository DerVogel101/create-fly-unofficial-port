package com.zurrtum.create.foundation.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Replacement for the overridable {@code BlockItem#updateCustomBlockEntityTag} that was removed in 26.3.
 * Called by {@link com.zurrtum.create.mixin.BlockItemMixin} in place of the static vanilla method.
 */
public interface CustomBlockEntityTagItem {
    boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState placedState);
}

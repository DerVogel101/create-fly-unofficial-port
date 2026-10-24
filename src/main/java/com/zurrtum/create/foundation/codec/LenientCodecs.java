package com.zurrtum.create.foundation.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Readers that also accept the data formats Create writes on other loaders (schematics and worlds from Create on
 * NeoForge/Forge): enum constant names ("WAITING"), directions as numbers, block positions as {X,Y,Z}, inventories as
 * {Size, Items:[{Slot, ...}]} and item stacks carrying components this game doesn't know. Everything is still written
 * in this mod's own format.
 */
public final class LenientCodecs {
    private LenientCodecs() {
    }

    /**
     * Enum codec writing the serialized name and reading it, the constant name or either in any letter case.
     */
    public static <E extends Enum<E> & StringRepresentable> Codec<E> enumOf(Supplier<E[]> values) {
        Codec<E> any = Codec.STRING.comapFlatMap(
            name -> {
                for (E value : values.get()) {
                    if (value.getSerializedName().equalsIgnoreCase(name) || value.name().equalsIgnoreCase(name)) {
                        return DataResult.success(value);
                    }
                }
                return DataResult.error(() -> "Unknown element name: " + name);
            }, StringRepresentable::getSerializedName
        );
        return Codec.withAlternative(StringRepresentable.fromEnum(values), any);
    }

    public static final Codec<Direction> DIRECTION = Codec.withAlternative(
        enumOf(Direction::values),
        Codec.INT.xmap(Direction::from3DDataValue, Direction::get3DDataValue)
    );

    public static final Codec<List<Direction>> DIRECTION_LIST = DIRECTION.listOf();

    public static final Codec<BlockPos> BLOCK_POS = Codec.withAlternative(
        BlockPos.CODEC, RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("X").forGetter(BlockPos::getX),
            Codec.INT.fieldOf("Y").forGetter(BlockPos::getY),
            Codec.INT.fieldOf("Z").forGetter(BlockPos::getZ)
        ).apply(i, BlockPos::new))
    );

    public static final Codec<List<BlockPos>> BLOCK_POS_LIST = BLOCK_POS.listOf();

    /**
     * Inventory stored as {Size, Items:[{Slot, id, count}]}: the stacks by slot, if the field has that shape.
     */
    public static Optional<List<ItemStackWithSlot>> slottedItems(ValueInput view, String key) {
        // read the raw value first: child() would report a problem for this mod's own format (a list) on every load/sync
        return view.read(key, Codec.PASSTHROUGH).flatMap(dynamic -> {
            if (!(dynamic.getValue() instanceof CompoundTag tag) || !(tag.get("Items") instanceof ListTag)) {
                return Optional.empty();
            }
            List<ItemStackWithSlot> out = new ArrayList<>();
            // like vanilla list reading: keep what decodes (partial result) when a stack carries data this game can't read
            dynamic.get("Items").asStream().forEach(item -> ItemStackWithSlot.CODEC.parse(item).resultOrPartial().ifPresent(out::add));
            return Optional.of(out);
        });
    }

    /**
     * Fills {@code stacks} from a {Size, Items} inventory; returns false if the field doesn't have that shape.
     */
    public static boolean readSlotted(ValueInput view, String key, List<ItemStack> stacks) {
        Optional<List<ItemStackWithSlot>> items = slottedItems(view, key);
        if (items.isEmpty()) {
            return false;
        }
        for (int i = 0; i < stacks.size(); i++) {
            stacks.set(i, ItemStack.EMPTY);
        }
        for (ItemStackWithSlot item : items.get()) {
            if (item.slot() >= 0 && item.slot() < stacks.size()) {
                stacks.set(item.slot(), item.stack());
            }
        }
        return true;
    }

    /**
     * Item stack that may carry components from other mods/loaders (e.g. a filter holding an item with data this game
     * doesn't know): retries without the unknown components instead of losing the whole stack.
     */
    public static Optional<ItemStack> itemStack(ValueInput view, String key) {
        Optional<ItemStack> direct = view.read(key, ItemStack.OPTIONAL_CODEC);
        if (direct.isPresent()) {
            return direct;
        }
        return view.read(key, CompoundTag.CODEC).flatMap(tag -> {
            CompoundTag copy = tag.copy();
            stripUnknownComponents(copy);
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, view.lookup());
            DataResult<ItemStack> result = ItemStack.OPTIONAL_CODEC.parse(ops, copy);
            if (result.isError()) {
                // last resort: keep the item itself, drop all of its data
                copy.remove("components");
                result = ItemStack.OPTIONAL_CODEC.parse(ops, copy);
            }
            return result.result();
        });
    }

    private static void stripUnknownComponents(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            compound.getCompound("components").ifPresent(components -> {
                for (String key : List.copyOf(components.keySet())) {
                    Identifier id = Identifier.tryParse(key.startsWith("!") ? key.substring(1) : key);
                    if (id == null || !BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(id)) {
                        components.remove(key);
                    }
                }
            });
            for (String key : compound.keySet()) {
                stripUnknownComponents(compound.get(key));
            }
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) {
                stripUnknownComponents(element);
            }
        }
    }
}

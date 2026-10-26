package probe;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.logistics.box.PackageItem;
import com.zurrtum.create.content.redstone.link.controller.LinkedControllerItem;
import com.zurrtum.create.foundation.item.ItemHelper;
import com.zurrtum.create.infrastructure.items.ItemStackHandler;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

public class InventorySlotProbe implements DedicatedServerModInitializer {
    private static int failures;

    private static List<ItemStack> sparse() {
        return List.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 3), ItemStack.EMPTY,
            ItemStack.EMPTY, new ItemStack(Items.GOLD_INGOT, 7), ItemStack.EMPTY);
    }

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            check("dense contents control", () -> {
                List<ItemStack> contents = List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.GOLD_INGOT, 7));
                ItemStackHandler target = new ItemStackHandler(6);
                ItemHelper.fillItemStackHandler(ItemContainerContents.fromItems(contents), target);
                expectSlots(target, contents);
            });
            check("sparse contents preserve slot indices", () -> {
                ItemStackHandler target = new ItemStackHandler(6);
                ItemHelper.fillItemStackHandler(ItemContainerContents.fromItems(sparse()), target);
                expectSlots(target, sparse());
            });
            check("copy clears stale destination slots", () -> {
                ItemStackHandler target = new ItemStackHandler(9);
                for (int i = 0; i < target.getContainerSize(); i++) target.setItem(i, new ItemStack(Items.IRON_INGOT, 16));
                ItemHelper.fillItemStackHandler(ItemContainerContents.fromItems(sparse()), target);
                expectSlots(target, sparse());
            });
            check("empty contents clear the destination", () -> {
                ItemStackHandler target = new ItemStackHandler(6);
                target.setItem(5, new ItemStack(Items.IRON_INGOT, 16));
                ItemHelper.fillItemStackHandler(ItemContainerContents.EMPTY, target);
                expectSlots(target, List.of());
            });
            check("linked controller keeps frequency slots", () -> {
                ItemStack controller = AllItems.LINKED_CONTROLLER.getDefaultInstance();
                controller.set(AllDataComponents.LINKED_CONTROLLER_ITEMS, ItemContainerContents.fromItems(sparse()));
                expectSlots(LinkedControllerItem.getFrequencyItems(controller), sparse());
            });
            check("list filter keeps its item slots", () -> {
                ItemStack filter = AllItems.FILTER.getDefaultInstance();
                filter.set(AllDataComponents.FILTER_ITEMS, ItemContainerContents.fromItems(sparse()));
                expectSlots(AllItems.FILTER.getFilterItemHandler(filter), sparse());
            });
            check("package keeps its content slots", () -> {
                ItemStack box = PackageItem.containing(sparse());
                expectSlots(PackageItem.getContents(box), sparse());
            });
            System.out.println("INVENTORY_PROBE_RESULT failures=" + failures);
        });
    }

    private static void expectSlots(ItemStackHandler actual, List<ItemStack> expected) {
        for (int i = 0; i < actual.getContainerSize(); i++) {
            ItemStack wanted = i < expected.size() ? expected.get(i) : ItemStack.EMPTY;
            if (!ItemStack.matches(actual.getItem(i), wanted)) {
                throw new AssertionError("slot " + i + ": expected " + wanted + ", got " + actual.getItem(i));
            }
        }
    }

    private static void check(String name, Runnable action) {
        try {
            action.run();
            System.out.println("INVENTORY_PROBE PASS " + name);
        } catch (Throwable failure) {
            failures++;
            System.out.println("INVENTORY_PROBE FAIL " + name);
            failure.printStackTrace();
        }
    }
}

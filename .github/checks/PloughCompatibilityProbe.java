package probe;

import com.mojang.authlib.GameProfile;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.contraptions.actors.plough.PloughMovementBehaviour;
import com.zurrtum.create.content.contraptions.bearing.BearingContraption;
import com.zurrtum.create.content.contraptions.behaviour.MovementContext;
import com.zurrtum.create.infrastructure.player.FakePlayerEntity;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.UUID;

public class PloughCompatibilityProbe implements DedicatedServerModInitializer {
    private static int failures;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel level = server.overworld();
            BlockPos actor = new BlockPos(8, 121, 8);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(actor.below()), Direction.UP, actor.below(), false);
            check("plough tills dirt", () -> {
                level.getChunkAt(actor);
                level.setBlockAndUpdate(actor, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(actor.below(), Blocks.DIRT.defaultBlockState());
                MovementContext context = new MovementContext(level,
                    new StructureBlockInfo(BlockPos.ZERO, AllBlocks.MECHANICAL_PLOUGH.defaultBlockState(), null),
                    new BearingContraption());
                new PloughMovementBehaviour().visitNewPosition(context, actor);
                require(level.getBlockState(actor.below()).is(Blocks.FARMLAND));
            });
            check("playerless plough off hand is not blocked", () ->
                require(!blocked(level, null, InteractionHand.OFF_HAND, hit)));
            FakePlayerEntity player = new FakePlayerEntity(level,
                new GameProfile(UUID.fromString("458eb140-d740-4a64-813c-8bdd5c949913"), "PloughProbe"));
            player.setItemInHand(InteractionHand.MAIN_HAND, Items.DIAMOND_HOE.getDefaultInstance());
            check("player with empty offhand is not blocked", () ->
                require(!blocked(level, player, InteractionHand.MAIN_HAND, hit)));
            player.setItemInHand(InteractionHand.OFF_HAND, Items.SHIELD.getDefaultInstance());
            check("player shield still blocks main hand", () ->
                require(blocked(level, player, InteractionHand.MAIN_HAND, hit)));
            check("offhand still bypasses shield check", () ->
                require(!blocked(level, player, InteractionHand.OFF_HAND, hit)));
            player.setShiftKeyDown(true);
            check("sneaking still bypasses shield check", () ->
                require(!blocked(level, player, InteractionHand.MAIN_HAND, hit)));
            System.out.println("PLOUGH_PROBE_RESULT failures=" + failures);
        });
    }

    private static boolean blocked(ServerLevel level, Player player, InteractionHand hand, BlockHitResult hit) throws Exception {
        Method method = BlockTransformer.class.getDeclaredMethod("playerHasBlockingItemUseIntent", UseOnContext.class);
        method.setAccessible(true);
        UseOnContext context = new UseOnContext(level, player, hand, Items.DIAMOND_HOE.getDefaultInstance(), hit) {};
        return (boolean) method.invoke(null, context);
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected tool-use behavior");
    }

    private static void check(String name, CheckedAction action) {
        try {
            action.run();
            System.out.println("PLOUGH_PROBE PASS " + name);
        } catch (Throwable failure) {
            failures++;
            System.out.println("PLOUGH_PROBE FAIL " + name);
            failure.printStackTrace();
        }
    }

    private interface CheckedAction {
        void run() throws Exception;
    }
}

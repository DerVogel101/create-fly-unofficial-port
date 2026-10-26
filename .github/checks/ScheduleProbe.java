package probe;

import com.mojang.authlib.GameProfile;
import com.zurrtum.create.*;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.graph.TrackGraph;
import com.zurrtum.create.content.trains.schedule.*;
import com.zurrtum.create.content.trains.schedule.condition.*;
import com.zurrtum.create.content.trains.schedule.destination.*;
import com.zurrtum.create.content.trains.display.GlobalTrainDisplayData;
import com.zurrtum.create.infrastructure.packet.c2s.ScheduleEditPacket;
import com.zurrtum.create.infrastructure.player.FakePlayerEntity;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;

import java.util.*;

public class ScheduleProbe implements DedicatedServerModInitializer {
    private static int failures;

    private static Schedule sample() {
        Schedule schedule = new Schedule();
        ScheduleEntry entry = new ScheduleEntry();
        entry.instruction = new DestinationInstruction(AllSchedules.DESTINATION);
        entry.instruction.getData().putString("Text", "Station A");
        ScheduledDelay delay = new ScheduledDelay(AllSchedules.DELAY);
        delay.getData().putInt("Value", 3);
        delay.getData().putInt("TimeUnit", 0);
        entry.conditions.add(new ArrayList<>(List.of(delay)));
        schedule.entries.add(entry);
        schedule.cyclic = false;
        return schedule;
    }

    private static void verify(Schedule schedule) {
        require(schedule != null && schedule.entries.size() == 1, "one schedule entry");
        ScheduleEntry entry = schedule.entries.getFirst();
        require(entry.instruction instanceof DestinationInstruction, "destination instruction");
        require(entry.instruction.getData().getStringOr("Text", "").equals("Station A"), "station text");
        require(entry.conditions.size() == 1 && entry.conditions.getFirst().size() == 1, "wait condition survives");
        require(entry.conditions.getFirst().getFirst() instanceof ScheduledDelay, "delay type");
        require(entry.conditions.getFirst().getFirst().intData("Value") == 3, "delay value");
        require(!schedule.cyclic, "non-looping flag");
    }

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel level = server.overworld();
            FakePlayerEntity player = new FakePlayerEntity(level, new GameProfile(UUID.randomUUID(), "ScheduleTest"));
            check("schedule NBT save/load", () -> {
                TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
                sample().write(output);
                System.out.println("SCHEDULE_PROBE NBT " + output.buildResult());
                verify(Schedule.read(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), output.buildResult())));
            });
            check("schedule packet encode/decode", () -> {
                RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                try {
                    ScheduleEditPacket.CODEC.encode(buffer, new ScheduleEditPacket(sample()));
                    verify(ScheduleEditPacket.CODEC.decode(buffer).schedule());
                } finally { buffer.release(); }
            });
            check("schedule menu opens from item use", () -> {
                player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.SCHEDULE.getDefaultInstance());
                AllItems.SCHEDULE.use(level, player, InteractionHand.MAIN_HAND);
                require(player.containerMenu instanceof ScheduleMenu, "schedule menu open");
            });
            check("schedule edit handler persists held item", () -> {
                player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.SCHEDULE.getDefaultInstance());
                AllHandle.onScheduleEdit(player.connection, new ScheduleEditPacket(sample()));
                verify(ScheduleItem.getSchedule(server.registryAccess(), player.getMainHandItem()));
            });
            check("schedule item network roundtrip", () -> {
                RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                try {
                    ItemStack.STREAM_CODEC.encode(buffer, player.getMainHandItem());
                    verify(ScheduleItem.getSchedule(server.registryAccess(), ItemStack.STREAM_CODEC.decode(buffer)));
                } finally { buffer.release(); }
            });
            check("schedule dynamic codec roundtrip", () -> {
                Tag tag = Schedule.encode(sample(), NbtOps.INSTANCE, NbtOps.INSTANCE.empty()).getOrThrow();
                verify(Schedule.decode(NbtOps.INSTANCE, tag));
            });
            check("schedule runtime executes instructions", () -> {
                Train train = train();
                Schedule schedule = new Schedule();
                ScheduleEntry rename = new ScheduleEntry();
                rename.instruction = new ChangeTitleInstruction(AllSchedules.RENAME);
                rename.instruction.getData().putString("Text", "Probe Service");
                ScheduleEntry throttle = new ScheduleEntry();
                throttle.instruction = new ChangeThrottleInstruction(AllSchedules.THROTTLE);
                throttle.instruction.getData().putInt("Value", 42);
                schedule.entries.add(rename);
                schedule.entries.add(throttle);
                schedule.cyclic = false;
                train.runtime.setSchedule(schedule, false);
                for (int i = 0; i < 130; i++) train.runtime.tick(level);
                require(train.runtime.currentTitle.equals("Probe Service"), "rename executed");
                require(Math.abs(train.throttle - 0.42f) < 0.001, "throttle executed");
                require(train.runtime.completed, "schedule finishes");
            });
            check("runtime wait conditions advance", () -> {
                Train train = train();
                train.runtime.setSchedule(sample(), false);
                train.runtime.state = ScheduleRuntime.State.IN_TRANSIT;
                train.runtime.destinationReached();
                for (int i = 0; i < 6 && train.runtime.state == ScheduleRuntime.State.POST_TRANSIT; i++) train.runtime.tickConditions(level);
                require(train.runtime.currentEntry == 1, "three-tick delay completes");
            });
            check("station timetable predictions populate", () -> {
                Train train = train();
                train.runtime.setSchedule(sample(), false);
                Create.RAILWAYS.trains.put(train.id, train);
                try {
                    GlobalTrainDisplayData.refresh();
                    require(!GlobalTrainDisplayData.prepare("Station A", 4).isEmpty(), "matching station prediction");
                } finally {
                    Create.RAILWAYS.trains.remove(train.id);
                    GlobalTrainDisplayData.refresh();
                }
            });
            System.out.println("SCHEDULE_PROBE_RESULT failures=" + failures);
        });
    }

    private static Train train() {
        return new Train(UUID.randomUUID(), UUID.randomUUID(), new TrackGraph(), new ArrayList<>(), new ArrayList<>(), false, 0);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void check(String name, CheckedAction action) {
        try { action.run(); System.out.println("SCHEDULE_PROBE PASS " + name); }
        catch (Throwable failure) { failures++; System.out.println("SCHEDULE_PROBE FAIL " + name); failure.printStackTrace(); }
    }

    private interface CheckedAction { void run() throws Exception; }
}

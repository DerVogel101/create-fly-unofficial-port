package com.zurrtum.create.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.Create;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
    @Inject(method = "initServer()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;loadLevel()V"))
    private void setupServer(CallbackInfoReturnable<Boolean> cir) {
        Create.SERVER = (IntegratedServer) (Object) this;
    }

    // Minecraft 26.3 race: when the owner disconnects, the server halts itself; the client's own halt() then queues a
    // blocking task (kicking other LAN players), and if it lands after the server's last task loop it never runs and
    // executeBlocking waits forever, so the client hangs on quit. Wait for it only while the server is still running.
    @WrapOperation(method = "halt(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"))
    private void skipBlockingTaskWhenStopping(IntegratedServer server, Runnable task, Operation<Void> original) {
        if (server.isSameThread()) {
            original.call(server, task);
            return;
        }
        if (!server.isRunning()) {
            return;
        }
        CompletableFuture<Void> future = server.submit(task);
        while (!future.isDone() && server.isRunning()) {
            try {
                future.get(50, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }
}

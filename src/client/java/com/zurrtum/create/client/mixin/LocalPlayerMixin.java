package com.zurrtum.create.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.AllSynchedDatas;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Inject(method = "isUnderWater()Z", at = @At("HEAD"), cancellable = true)
    private void isSubmergedInWater(CallbackInfoReturnable<Boolean> cir) {
        if (AllSynchedDatas.HEAVY_BOOTS.get((LocalPlayer) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    // Carriages report isLocalInstanceAuthoritative() == true so vanilla never overwrites the carriage position
    // Create computes on the client. That also makes every seated passenger send ServerboundMoveVehiclePacket each
    // tick. 26.2 silently ignored those; 26.3 answers a passenger that isn't the controlling passenger with a
    // ClientboundMoveVehiclePacket reset (absSnapTo on the client) -> jittering train. The server never uses these
    // packets for carriages, so don't send them.
    @WrapOperation(method = "sendChanges()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isLocalInstanceAuthoritative()Z"))
    private boolean create$skipCarriageVehicleMove(Entity vehicle, Operation<Boolean> original) {
        if (vehicle instanceof CarriageContraptionEntity) {
            return false;
        }
        return original.call(vehicle);
    }
}

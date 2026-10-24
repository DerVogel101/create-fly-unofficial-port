package com.zurrtum.create.client.mixin;

import com.zurrtum.create.client.Create;
import com.zurrtum.create.client.content.contraptions.wrench.RadialWrenchHandler;
import com.zurrtum.create.client.content.equipment.toolbox.ToolboxHandlerClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    // every exit of keyPress, telling press/repeat from release by the GLFW action: picking the release path by return
    // ordinal broke in 26.3 (ordinal 5 became the method end, so every key event arrived as release + press and the
    // schematic tool menu (Alt) never closed)
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("RETURN"))
    private void onKey(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (handle == minecraft.getWindow().handle()) {
            onKey(event, action != 0);
        }
    }

    @Unique
    private void onKey(KeyEvent input, boolean pressed) {
        if (minecraft.gui.screen() != null) {
            return;
        }
        if (Create.SCHEMATIC_HANDLER.onKeyInput(input, pressed)) {
            return;
        }
        if (ToolboxHandlerClient.onKeyInput(minecraft, input)) {
            return;
        }
        RadialWrenchHandler.onKeyInput(minecraft, input, pressed);
    }
}

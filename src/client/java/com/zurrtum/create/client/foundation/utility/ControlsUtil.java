package com.zurrtum.create.client.foundation.utility;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.List;

public class ControlsUtil {

    private static @Nullable List<KeyMapping> standardControls;

    public static List<KeyMapping> getControls() {
        if (standardControls == null) {
            Options gameSettings = Minecraft.getInstance().options;
            standardControls = new ArrayList<>(6);
            standardControls.add(gameSettings.keyUp);
            standardControls.add(gameSettings.keyDown);
            standardControls.add(gameSettings.keyLeft);
            standardControls.add(gameSettings.keyRight);
            standardControls.add(gameSettings.keyJump);
            standardControls.add(gameSettings.keyShift);
        }
        return standardControls;
    }

    public static boolean isActuallyPressed(Minecraft mc, KeyMapping kb) {
        InputConstants.Key key = kb.key;
        int button = key.getValue();
        if (key.getType() == InputConstants.Type.MOUSE) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                int buttons = SDLMouse.SDL_GetMouseState(stack.mallocFloat(1), stack.mallocFloat(1));
                // SDL_BUTTON_MASK(button)
                return (buttons & (1 << (button - 1))) != 0;
            }
        }
        return InputConstants.isKeyDown(button);
    }

}

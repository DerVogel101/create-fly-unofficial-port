package com.zurrtum.create.client.ponder.enums;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;

import static com.zurrtum.create.client.ponder.Ponder.MOD_ID;

public class PonderKeybinds {
    // Compile-time constant: reading it doesn't initialize this class (see KeyMappingMixin)
    public static final String PONDER_NAME = "key.ponder.ponder";
    public static final Category CATEGORY = Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "binding"));
    public static final KeyMapping PONDER = register(PONDER_NAME, InputConstants.KEY_W);

    private static KeyMapping register(String name, int defaultKey) {
        KeyMapping keyBinding = new KeyMapping(name, InputConstants.UNKNOWN.getValue(), CATEGORY);
        Key key = InputConstants.Type.KEYBOARD.getOrCreate(defaultKey);
        keyBinding.defaultKey = key;
        keyBinding.setKey(key);
        return keyBinding;
    }

    public static void register() {
    }
}

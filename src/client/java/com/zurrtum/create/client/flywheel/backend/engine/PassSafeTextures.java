package com.zurrtum.create.client.flywheel.backend.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flywheel draws inside Renderpearl's level render pass, where uploading a texture is not allowed. Textures that are not
 * loaded yet are queued and replaced by the missing texture for that frame, then loaded before the next level render.
 */
public final class PassSafeTextures {
    private static final Set<Identifier> PENDING = ConcurrentHashMap.newKeySet();

    private PassSafeTextures() {
    }

    public static AbstractTexture get(Identifier id) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        AbstractTexture texture = textureManager.byPath.get(id);
        if (texture != null) {
            return texture;
        }
        PENDING.add(id);
        return textureManager.getTexture(MissingTextureAtlasSprite.getLocation());
    }

    /**
     * Must be called while no render pass is open.
     */
    public static void loadPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        for (Identifier id : PENDING) {
            textureManager.getTexture(id);
        }
        PENDING.clear();
    }
}

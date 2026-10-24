package com.zurrtum.create.client.flywheel.backend;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.zurrtum.create.client.flywheel.lib.util.ResourceUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.UnknownNullability;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;

public class NoiseTextures {
    public static final Identifier NOISE_TEXTURE = ResourceUtil.rl("textures/flywheel/noise/blue.png");

    @UnknownNullability
    public static AbstractTexture BLUE_NOISE;

    public static void reload(ResourceManager manager) {
        if (BLUE_NOISE != null) {
            BLUE_NOISE.close();
            BLUE_NOISE = null;
        }
        var optional = manager.getResource(NOISE_TEXTURE);

        if (optional.isEmpty()) {
            return;
        }

        try (var is = optional.get().open(); var image = NativeImage.read(is)) {
            BLUE_NOISE = new NoiseTexture(toLuminance(image));
        } catch (IOException e) {

        }
    }

    // 26.3 always loads images as RGBA, but the noise texture is uploaded as a single R8 channel
    private static NativeImage toLuminance(NativeImage rgba) {
        int width = rgba.getWidth();
        int height = rgba.getHeight();
        NativeImage luminance = new NativeImage(NativeImage.Format.LUMINANCE, width, height, false);
        long ptr = luminance.getPointer();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                MemoryUtil.memPutByte(ptr + x + (long) y * width, (byte) ARGB.red(rgba.getPixel(x, y)));
            }
        }
        return luminance;
    }

    private static class NoiseTexture extends AbstractTexture {
        private final NativeImage pixels;

        public NoiseTexture(NativeImage image) {
            pixels = image;
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(
                () -> "Flywheel Blue Noise",
                5,
                GpuFormat.R8_UNORM,
                pixels.getWidth(),
                pixels.getHeight(),
                1,
                1
            );
            sampler = RenderSystem.getSamplerCache()
                .getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, false);
            textureView = device.createTextureView(texture);
            device.createCommandEncoder().writeToTexture(texture, pixels);
        }

        @Override
        public void close() {
            pixels.close();
            super.close();
        }
    }
}

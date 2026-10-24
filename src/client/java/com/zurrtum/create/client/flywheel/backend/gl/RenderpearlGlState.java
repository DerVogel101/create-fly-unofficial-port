package com.zurrtum.create.client.flywheel.backend.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.backend.opengl.GlCommandEncoder;
import com.mojang.renderpearl.backend.opengl.GlDevice;
import com.mojang.renderpearl.backend.opengl.GlRenderPass;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import it.unimi.dsi.fastutil.booleans.BooleanList;

/**
 * Flywheel draws with raw OpenGL inside Renderpearl render passes. Renderpearl caches what it has bound per pass and
 * per encoder, so after Flywheel draws it has to be told to bind everything again. Does nothing on other backends.
 */
public final class RenderpearlGlState {
    private RenderpearlGlState() {
    }

    /**
     * Renderpearl skips binding a pipeline it thinks is still bound. Clearing that makes its next draw restore its
     * program, depth, blend, cull and write-mask state.
     */
    public static void invalidatePipelineCache() {
        if (RenderSystem.getDevice() instanceof FrontendGpuDevice frontend && frontend.backend instanceof GlDevice device && device.createCommandEncoder() instanceof GlCommandEncoder encoder) {
            encoder.lastPipeline = null;
        }
    }

    /**
     * Marks everything the pass has bound (uniform buffers, samplers, vertex/index buffers, push constants, scissor)
     * as dirty, so its next draw binds it again.
     */
    public static void invalidateRenderPass(RenderPass renderPass) {
        if (!(renderPass instanceof FrontendRenderPass frontendPass) || !(frontendPass.backend instanceof GlRenderPass pass)) {
            return;
        }
        pass.vertexBufferDirty = true;
        pass.indexBufferDirty = pass.indexBuffer != null;
        pass.pushConstantsDirty = pass.pushConstants != null;
        pass.scissorStateDirty = true;
        BooleanList dirtyUniforms = pass.dirtyUniforms;
        for (int i = 0, size = dirtyUniforms.size(); i < size; i++) {
            dirtyUniforms.set(i, true);
        }
        pass.anyUniformDirty = !dirtyUniforms.isEmpty();
        invalidatePipelineCache();
    }
}

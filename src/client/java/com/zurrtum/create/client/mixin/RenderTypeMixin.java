package com.zurrtum.create.client.mixin;

import com.zurrtum.create.client.catnip.render.CustomRenderType;
import com.zurrtum.create.client.foundation.render.CustomRenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderType.class)
public class RenderTypeMixin implements CustomRenderType {
    @Shadow
    @Final
    public RenderSetup state;
    @Shadow
    @Final
    protected String name;
    @Unique
    private boolean solidBlend;
    @Unique
    private boolean blendWithoutOit;
    @Unique
    private boolean priority;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(String name, RenderSetup state, CallbackInfo ci) {
        solidBlend = ((CustomRenderPipeline) state.pipeline).create$isSolidBlend();
        blendWithoutOit = state.oitPipelineSet == null && (name.startsWith("create") || name.startsWith("ponder:") || name.startsWith("catnip:"));
    }

    @Inject(method = "hasBlending()Z", at = @At("HEAD"), cancellable = true)
    private void skipBlendCheck(CallbackInfoReturnable<Boolean> cir) {
        if (solidBlend) {
            cir.setReturnValue(false);
        } else if (blendWithoutOit && create$improvedTransparency()) {
            // Since 26.3, Improved Transparency draws translucent features through OIT pipelines, which Create's
            // custom shaders don't have. Draw them blended in the solid phase instead of crashing.
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static boolean create$improvedTransparency() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.gameRenderer != null && minecraft.gameRenderer.useImprovedTransparency();
    }

    @Override
    public boolean create$isPriority() {
        return priority;
    }

    @Override
    public void create$markPriority() {
        priority = true;
    }
}

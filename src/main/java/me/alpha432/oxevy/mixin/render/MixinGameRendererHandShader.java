package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.HandsShaderModule;
import me.alpha432.oxevy.util.render.ShaderUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRendererHandShader {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void afterRenderLevel(DeltaTracker tickCounter, CallbackInfo ci) {
        if (Oxevy.moduleManager == null) return;
        HandsShaderModule handsShader = Oxevy.moduleManager.getModuleByClass(HandsShaderModule.class);
        if (handsShader != null && handsShader.isEnabled()) {
            handsShader.renderEffect();
        }
    }
}

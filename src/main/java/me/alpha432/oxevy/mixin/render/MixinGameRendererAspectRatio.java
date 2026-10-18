package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.AspectRatioModule;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRendererAspectRatio {

    @Shadow
    private float getDepthFar() {
        return 0f;
    }

    @Inject(method = "getProjectionMatrix(F)Lorg/joml/Matrix4f;", at = @At("HEAD"), cancellable = true)
    private void onGetProjectionMatrix(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        AspectRatioModule ar = Oxevy.moduleManager == null
                ? null
                : Oxevy.moduleManager.getModuleByClass(AspectRatioModule.class);
        if (ar == null || !ar.isEnabled()) return;
        float far = getDepthFar();
        float aspect = ar.ratio.getValue();
        cir.setReturnValue(new Matrix4f().perspective(fov * ((float) Math.PI / 180), aspect, 0.05f, far));
    }
}

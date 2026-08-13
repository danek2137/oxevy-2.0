package me.alpha432.oxevy.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoFogModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V", at = @At("HEAD"), cancellable = true)
    private static void onSetupFog(Camera camera, FogRenderer.FogMode fogMode, float start, boolean b, float end, CallbackInfo ci) {
        NoFogModule noFog = Oxevy.moduleManager.getModuleByClass(NoFogModule.class);
        if (noFog != null && noFog.isEnabled()) {
            RenderSystem.setShaderFogStart(1000.0F);
            RenderSystem.setShaderFogEnd(2000.0F);
            ci.cancel();
        }
    }
}

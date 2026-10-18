package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.event.impl.render.SkyEvent;
import me.alpha432.oxevy.features.modules.render.CustomSkyModule;
import me.alpha432.oxevy.util.render.ShaderUtil;
import me.alpha432.oxevy.util.traits.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class MixinSkyRenderer {

    private CustomSkyModule oxevy$getSky() {
        return me.alpha432.oxevy.Oxevy.moduleManager == null
                ? null
                : me.alpha432.oxevy.Oxevy.moduleManager.getModuleByClass(CustomSkyModule.class);
    }

    @Inject(method = "renderSkyDisc(I)V", at = @At("HEAD"), cancellable = true)
    private void onRenderSkyDisc(int skyColor, CallbackInfo ci) {
        CustomSkyModule sky = oxevy$getSky();
        if (sky == null || !sky.isEnabled()) return;
        ShaderUtil.initShadersIfNeeded();
        if (!ShaderUtil.isInitialized() || ShaderUtil.getSkyShader() == null) return;
        ci.cancel();
        int top = sky.getSkyColor();
        int horizon = darken(top, 0.65f);
        int bottom = darken(top, 0.35f);
        ShaderUtil.renderSkyShader(
                ShaderUtil.getGameTime(),
                top, horizon, bottom,
                sky.starDensity.getValue(),
                sky.starBrightness.getValue());
    }

    private static int darken(int color, float factor) {
        int a = color >>> 24;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/SkyRenderState;)V", at = @At("TAIL"))
    private void onExtractRenderState(ClientLevel level, float tickDelta, Camera camera, SkyRenderState state, CallbackInfo ci) {
        SkyEvent event = SkyEvent.INSTANCE.reset();
        Util.EVENT_BUS.post(event);
        if (event.isCancelled() && event.hasSkyColor()) {
            state.skyColor = event.getSkyColor();
            state.sunriseAndSunsetColor = event.getSkyColor();
        }
        if (event.isCancelled() && event.shouldHideStars()) {
            state.starBrightness = 0.0f;
        }
    }

    @Inject(method = "renderSunMoonAndStars(Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V", at = @At("HEAD"), cancellable = true)
    private void onRenderSunMoonAndStars(PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float starBrightness, float skyDarkness, CallbackInfo ci) {
        if (SkyEvent.INSTANCE.isCancelled() && SkyEvent.INSTANCE.shouldHideCelestial()) {
            ci.cancel();
        }
    }
}

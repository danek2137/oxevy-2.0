package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoRenderModule;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class MixinNoRenderScreenEffect {
    @Inject(method = "renderScreenEffect", at = @At("HEAD"), cancellable = true)
    private void onRenderScreenEffect(boolean bl, float f, CallbackInfo ci) {
        NoRenderModule noRender = Oxevy.moduleManager.getModuleByClass(NoRenderModule.class);
        if (noRender.isEnabled() && (noRender.noFireOverlay() || noRender.noLiquidOverlay() || noRender.noInWallOverlay()))
            ci.cancel();
    }
}

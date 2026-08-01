package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoRenderModule;
import net.minecraft.client.renderer.entity.FireworkEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkEntityRenderer.class)
public class MixinNoRenderFirework {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void onRenderFirework(CallbackInfo ci) {
        NoRenderModule noRender = Oxevy.moduleManager.getModuleByClass(NoRenderModule.class);
        if (noRender.noFireworkExplosions()) ci.cancel();
    }
}

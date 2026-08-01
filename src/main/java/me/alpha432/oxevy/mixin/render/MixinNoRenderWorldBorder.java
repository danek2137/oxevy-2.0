package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoRenderModule;
import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRenderer.class)
public class MixinNoRenderWorldBorder {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderWorldBorder(CallbackInfo ci) {
        NoRenderModule noRender = Oxevy.moduleManager.getModuleByClass(NoRenderModule.class);
        if (noRender.noWorldBorder()) ci.cancel();
    }
}

package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NameTagsModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NameTagFeatureRenderer.class)
public class MixinNameTagRenderer {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource, Font font, CallbackInfo ci) {
        NameTagsModule nameTags = Oxevy.moduleManager.getModuleByClass(NameTagsModule.class);
        if (nameTags != null && nameTags.isEnabled()) {
            ci.cancel();
        }
    }
}

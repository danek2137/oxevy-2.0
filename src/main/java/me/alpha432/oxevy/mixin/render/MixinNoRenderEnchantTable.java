package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoRenderModule;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantTableRenderer.class)
abstract class MixinNoRenderEnchantTable {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/EnchantTableRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderEnchantTable(CallbackInfo ci) {
        NoRenderModule noRender = Oxevy.moduleManager.getModuleByClass(NoRenderModule.class);
        if (noRender.noEnchTableBook()) ci.cancel();
    }
}

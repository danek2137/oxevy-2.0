package me.alpha432.oxevy.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoHurtcamModule;
import me.alpha432.oxevy.features.modules.render.NoRenderModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Inject(method = "bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"), cancellable = true)
    private void onBobHurt(PoseStack matrices, float partialTicks, CallbackInfo ci) {
        NoHurtcamModule noHurtcam = Oxevy.moduleManager.getModuleByClass(NoHurtcamModule.class);
        if (noHurtcam.isEnabled())
            ci.cancel();
    }

    @Inject(method = "renderConfusionOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("HEAD"), cancellable = true)
    private void onRenderConfusionOverlay(GuiGraphics graphics, float f, CallbackInfo ci) {
        NoRenderModule noRender = Oxevy.moduleManager.getModuleByClass(NoRenderModule.class);
        if (noRender.noNausea()) ci.cancel();
    }
}

package me.alpha432.oxevy.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.CustomFOVModule;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class MixinGameRendererFOV {
    @ModifyReturnValue(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"))
    private float onGetFov(float original) {
        CustomFOVModule m = Oxevy.moduleManager == null ? null : Oxevy.moduleManager.getModuleByClass(CustomFOVModule.class);
        if (m == null || !m.isEnabled()) return original;
        return m.fov.getValue();
    }
}

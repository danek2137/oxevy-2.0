package me.alpha432.oxevy.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoFogModule;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @ModifyReturnValue(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;Lorg/joml/Vector4f;FZF)Lnet/minecraft/client/renderer/FogParameters;", at = @At("RETURN"))
    private static FogParameters modifyFogParameters(FogParameters original) {
        NoFogModule noFog = Oxevy.moduleManager.getModuleByClass(NoFogModule.class);
        if (noFog != null && noFog.isEnabled()) {
            return FogParameters.NO_FOG;
        }
        return original;
    }
}

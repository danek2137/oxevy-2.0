package me.alpha432.oxevy.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.SkyEvent;
import me.alpha432.oxevy.features.modules.render.NoFogModule;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @ModifyReturnValue(method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lorg/joml/Vector4f;", at = @At("RETURN"))
    private Vector4f modifyFogColor(Vector4f fog) {
        SkyEvent sky = SkyEvent.INSTANCE;
        if (sky.isCancelled() && sky.hasFogColor()) {
            int c = sky.getFogColor();
            return new Vector4f((c >> 16 & 0xFF) / 255.0f, (c >> 8 & 0xFF) / 255.0f, (c & 0xFF) / 255.0f, 1.0f);
        }
        NoFogModule noFog = Oxevy.moduleManager.getModuleByClass(NoFogModule.class);
        if (noFog.isEnabled()) {
            return new Vector4f(0, 0, 0, 0);
        }
        return fog;
    }
}

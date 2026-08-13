package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.AntiBlindModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "doesMobEffectBlockSky", at = @At("HEAD"), cancellable = true)
    private void onDoesMobEffectBlockSky(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        AntiBlindModule antiBlind = Oxevy.moduleManager.getModuleByClass(AntiBlindModule.class);
        if (antiBlind.isEnabled())
            cir.setReturnValue(false);
    }
}

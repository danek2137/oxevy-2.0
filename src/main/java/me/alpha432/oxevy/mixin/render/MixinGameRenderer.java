package me.alpha432.oxevy.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes a crash in GameRenderer.extractCamera when the camera's entity is null.
 * This can happen during world loading, dimension changes, or camera transitions
 * when the player entity hasn't been fully initialized yet.
 */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    /**
     * Guard extractCamera against null camera entity.
     * The vanilla code assumes camera.entity is never null, but it can be
     * during world loading, dimension changes, or when the camera is detached.
     */
    @Inject(method = "extractCamera", at = @At("HEAD"), cancellable = true)
    private void onExtractCamera(float partialTick, CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        Camera camera = renderer.getMainCamera();
        
        // If camera has no entity, skip the extraction to prevent NPE
        if (camera != null && camera.entity() == null) {
            ci.cancel();
        }
    }
}
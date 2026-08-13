package me.alpha432.oxevy.mixin.entity;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.movement.FreeCam;
import me.alpha432.oxevy.features.modules.render.FreeLookModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MixinFreecamLocalPlayer extends AbstractClientPlayer {

    private MixinFreecamLocalPlayer() {
        super(null, null);
    }

    @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
    private void onIsShiftKeyDown(CallbackInfoReturnable<Boolean> cir) {
        FreeCam freecam = Oxevy.moduleManager.getModuleByClass(FreeCam.class);
        if (freecam.isEnabled()) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public void turn(double deltaYaw, double deltaPitch) {
        FreeCam freecam = Oxevy.moduleManager.getModuleByClass(FreeCam.class);
        if (freecam.isEnabled()) {
            freecam.turn(deltaYaw, deltaPitch);
            return;
        }

        FreeLookModule freelook = Oxevy.moduleManager.getModuleByClass(FreeLookModule.class);
        if (freelook.isEnabled() && freelook.mode.getValue() == FreeLookModule.Mode.Camera) {
            freelook.cameraYaw += (float) deltaYaw;
            freelook.cameraPitch += (float) deltaPitch;
            freelook.cameraPitch = Mth.clamp(freelook.cameraPitch, -90, 90);
            return;
        }

        super.turn(deltaYaw, deltaPitch);
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void onAiStep(CallbackInfo ci) {
        FreeCam freecam = Oxevy.moduleManager.getModuleByClass(FreeCam.class);
        if (freecam.isEnabled()) {
            ci.cancel();
        }
    }
}

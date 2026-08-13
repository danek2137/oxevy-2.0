package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.movement.FreeCam;
import me.alpha432.oxevy.features.modules.render.FreeLookModule;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("RETURN"))
    private void onSetup(BlockGetter level, Entity entity, boolean detached, boolean thirdPerson, float partialTicks, CallbackInfo ci) {
        FreeCam freecam = Oxevy.moduleManager.getModuleByClass(FreeCam.class);
        if (freecam.isEnabled()) {
            this.detached = true;
            setPosition(freecam.getCamPos(partialTicks));
            setRotation(freecam.getCamYaw(), freecam.getCamPitch());
            return;
        }

        FreeLookModule freelook = Oxevy.moduleManager.getModuleByClass(FreeLookModule.class);
        if (freelook.isEnabled() && freelook.mode.getValue() == FreeLookModule.Mode.Camera) {
            setRotation(freelook.getCamYaw(), freelook.getCamPitch());
        }
    }
}

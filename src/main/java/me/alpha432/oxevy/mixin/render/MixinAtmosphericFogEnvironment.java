package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.SkyEvent;
import me.alpha432.oxevy.features.modules.render.NoFogModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AtmosphericFogEnvironment.class)
public class MixinAtmosphericFogEnvironment {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void onApplyStartEndModifier(FogData data, Camera camera, ClientLevel world, float viewDistance, DeltaTracker tickCounter, CallbackInfo ci) {
        SkyEvent sky = SkyEvent.INSTANCE;
        if (sky.isCancelled() && sky.hasFogDistance()) {
            data.environmentalStart = sky.getFogDistance();
            data.environmentalEnd = sky.getFogDistance();
        }
        NoFogModule noFog = Oxevy.moduleManager.getModuleByClass(NoFogModule.class);
        if (!noFog.isEnabled()) return;
        data.environmentalStart = 1000000;
        data.environmentalEnd = 1000000;
    }
}

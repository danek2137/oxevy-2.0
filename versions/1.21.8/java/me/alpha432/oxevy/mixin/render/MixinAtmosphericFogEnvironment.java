package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoFogModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AtmosphericFogEnvironment.class)
public class MixinAtmosphericFogEnvironment {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void onApplyStartEndModifier(FogData data, Entity entity, BlockPos pos, ClientLevel world, float viewDistance, DeltaTracker tickCounter, CallbackInfo ci) {
        NoFogModule noFog = Oxevy.moduleManager.getModuleByClass(NoFogModule.class);
        if (!noFog.isEnabled()) return;
        data.environmentalStart = 1000000;
        data.environmentalEnd = 1000000;
    }
}

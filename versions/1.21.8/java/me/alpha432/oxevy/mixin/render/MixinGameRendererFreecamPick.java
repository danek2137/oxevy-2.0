package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.movement.FreeCam;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class MixinGameRendererFreecamPick {
    @Inject(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At("HEAD"), cancellable = true)
    private static void onPick(Entity entity, double maxDist, double delta, float partialTicks, CallbackInfoReturnable<HitResult> cir) {
        FreeCam freecam = Oxevy.moduleManager.getModuleByClass(FreeCam.class);
        if (!freecam.isEnabled()) return;

        Vec3 camPos = freecam.getCamPos(partialTicks);
        Vec3 camEnd = camPos.add(freecam.getScaledCamDir(maxDist));

        HitResult blockHit = entity.level().clip(new ClipContext(camPos, camEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));

        Vec3 camStart = freecam.getCamPos(1.0F);
        Vec3 scaledDir = freecam.getScaledCamDir(maxDist);
        Vec3 entityEnd = camStart.add(scaledDir);
        AABB bounds = EntityType.PLAYER.getDimensions().makeBoundingBox(camStart).expandTowards(scaledDir).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(entity, camStart, entityEnd, bounds, e -> !e.isSpectator() && e.isPickable(), maxDist * maxDist);

        if (entityHit != null && blockHit != null) {
            double blockDist = camStart.distanceToSqr(blockHit.getLocation());
            double entityDist = camStart.distanceToSqr(entityHit.getLocation());
            cir.setReturnValue(blockDist < entityDist ? blockHit : entityHit);
        } else if (entityHit != null) {
            cir.setReturnValue(entityHit);
        } else {
            cir.setReturnValue(blockHit);
        }
    }
}

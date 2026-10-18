package me.alpha432.oxevy.mixin.entity;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.ItemPhysicsModule;
import me.alpha432.oxevy.util.interfaces.ItemPhysicsSim;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side visual physics for dropped items. Pure eye-candy: we never touch
 * the entity's real position/velocity (that is server-authoritative), we only
 * track a tumble pitch and a landing bounce that the renderer adds on top.
 */
@Mixin(ItemEntity.class)
public abstract class MixinItemEntity implements ItemPhysicsSim {

    @Unique private float oxevy$bounce;
    @Unique private float oxevy$bouncePrev;
    @Unique private float oxevy$bounceVel;
    @Unique private float oxevy$pitch;
    @Unique private float oxevy$pitchPrev;
    @Unique private float oxevy$restPitch = Float.MIN_VALUE;
    @Unique private boolean oxevy$wasOnGround;
    @Unique private double oxevy$prevVelY;

    @Override
    public float oxevyGetBounce() { return oxevy$bounce; }

    @Override
    public float oxevyGetBouncePrev() { return oxevy$bouncePrev; }

    @Override
    public float oxevyGetPitch() { return oxevy$pitch; }

    @Override
    public float oxevyGetPitchPrev() { return oxevy$pitchPrev; }

    @Inject(method = "tick", at = @At("TAIL"))
    private void oxevy$onTick(CallbackInfo ci) {
        ItemPhysicsModule m = Oxevy.moduleManager == null
                ? null
                : Oxevy.moduleManager.getModuleByClass(ItemPhysicsModule.class);

        if (m == null || !m.isEnabled()) {
            oxevy$bouncePrev = oxevy$bounce = 0f;
            oxevy$bounceVel = 0f;
            oxevy$pitchPrev = oxevy$pitch = 0f;
            oxevy$wasOnGround = false;
            oxevy$prevVelY = 0.0;
            return;
        }

        // save previous values so the renderer can interpolate between ticks
        oxevy$bouncePrev = oxevy$bounce;
        oxevy$pitchPrev = oxevy$pitch;

        ItemEntity self = (ItemEntity) (Object) this;
        Vec3 vel = self.getDeltaMovement();
        boolean grounded = self.onGround();
        double velY = vel.y;

        // tumble: spin the pitch while airborne/moving, settle when resting
        if (!grounded) {
            float horiz = (float) Math.min(1.5, vel.horizontalDistance());
            float fall = (float) Math.max(0.0, -velY);
            float rate = (1.8f + horiz * 1.2f + fall * 0.5f) * m.tumble.getValue();
            oxevy$pitch += rate * 0.05f;
        } else {
            if (m.randomRotation.getValue() && oxevy$restPitch == Float.MIN_VALUE) {
                oxevy$restPitch = (self.bobOffs - 0.5f) * 0.4f;
            }
            float rest = m.randomRotation.getValue() ? oxevy$restPitch : 0f;
            oxevy$pitch += (rest - oxevy$pitch) * 0.2f;
            if (Math.abs(oxevy$pitch - rest) < 0.01f) oxevy$pitch = rest;
        }

        // bounce: trigger once on landing, then spring back to rest
        if (grounded && !oxevy$wasOnGround && oxevy$prevVelY < -0.1) {
            float impact = (float) Math.min(Math.abs(oxevy$prevVelY), 3.0);
            oxevy$bounceVel = impact * 0.035f * m.bounce.getValue();
            if (oxevy$bounceVel < 0.02f) oxevy$bounceVel = 0f;
        }
        if (oxevy$bounceVel != 0f || oxevy$bounce != 0f) {
            oxevy$bounceVel -= 0.04f;
            oxevy$bounce += oxevy$bounceVel;
            if (oxevy$bounce <= 0f) {
                oxevy$bounce = 0f;
                oxevy$bounceVel = 0f;
            }
        }

        oxevy$wasOnGround = grounded;
        oxevy$prevVelY = velY;
    }
}

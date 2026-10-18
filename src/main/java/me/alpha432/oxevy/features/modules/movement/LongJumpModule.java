package me.alpha432.oxevy.features.modules.movement;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.world.phys.Vec3;

public class LongJumpModule extends Module {

    public final Setting<Float> boost = num("Boost", 1.6f, 1.0f, 3.0f);
    public final Setting<Boolean> onlySprint = bool("OnlySprint", true);

    private boolean awaitingAirborne;
    private boolean boosted;

    public LongJumpModule() {
        super("LongJump", "Boosts your forward momentum when jumping", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        awaitingAirborne = false;
        boosted = false;
    }

    @Override
    public void onDisable() {
        awaitingAirborne = false;
        boosted = false;
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        if (mc.player.onGround()) {
            if (boosted) {
                boosted = false;
            }
            boolean jumping = mc.options.keyJump.isDown();
            boolean sprinting = !onlySprint.getValue() || mc.player.isSprinting();
            boolean moving = mc.player.zza > 0.1f;
            awaitingAirborne = jumping && sprinting && moving;
        } else {
            if (awaitingAirborne && !boosted && mc.player.getDeltaMovement().y > 0) {
                Vec3 motion = mc.player.getDeltaMovement();
                Vec3 look = mc.player.getLookAngle();
                Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
                if (flatLook.lengthSqr() > 1.0E-4) {
                    flatLook = flatLook.normalize();
                    double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z) * boost.getValue();
                    mc.player.setDeltaMovement(flatLook.x * speed, motion.y, flatLook.z * speed);
                    boosted = true;
                }
            }
            if (mc.player.onGround()) {
                awaitingAirborne = false;
            }
        }
    }
}

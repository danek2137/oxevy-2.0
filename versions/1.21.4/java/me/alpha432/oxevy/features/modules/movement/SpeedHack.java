package me.alpha432.oxevy.features.modules.movement;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class SpeedHack extends Module {

    private final Setting<Mode> mode = mode("Mode", Mode.VANILLA);
    private final Setting<Float> speed = num("Speed", 1.0f, 0.1f, 5.0f);
    private final Setting<Boolean> autoSprint = bool("AutoSprint", true);
    private final Setting<Boolean> onGroundOnly = bool("OnGroundOnly", false);
    private final Setting<Boolean> effects = bool("Effects", false);
    private final Setting<Integer> effectAmplifier = num("EffectAmplifier", 1, 0, 5);

    private int tickCounter = 0;

    public SpeedHack() {
        super("SpeedBuff", "Gives you a speed boost", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        // Auto sprint
        if (autoSprint.getValue() && mc.player.zza > 0) {
            mc.player.setSprinting(true);
        }

        // Apply speed effect if enabled
        if (effects.getValue() && !mc.player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    999999, effectAmplifier.getValue(), false, false, false));
        }

        // Don't apply speed if conditions aren't met
        if (onGroundOnly.getValue() && !mc.player.onGround()) return;
        if (mc.player.zza <= 0 && mc.player.xxa == 0) return;

        // Apply speed based on mode
        switch (mode.getValue()) {
            case VANILLA:
                vanillaSpeed();
                break;
            case STRAFE:
                strafeSpeed();
                break;
            case BOOST:
                boostSpeed();
                break;
            case BHOP:
                bhopSpeed();
                break;
        }
    }

    private void vanillaSpeed() {
        // Simple vanilla-like speed boost
        double speedValue = speed.getValue().doubleValue();

        if (mc.player.onGround()) {
            mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x * speedValue,
                    mc.player.getDeltaMovement().y,
                    mc.player.getDeltaMovement().z * speedValue
            );
        } else {
            // Less speed in air
            mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x * (speedValue * 0.8),
                    mc.player.getDeltaMovement().y,
                    mc.player.getDeltaMovement().z * (speedValue * 0.8)
            );
        }
    }

    private void strafeSpeed() {
        // Strafe speed - maintains speed while changing direction
        Player player = mc.player;

        double forward = player.zza;
        double strafe = player.xxa;
        float yaw = player.getYRot();

        if (forward == 0 && strafe == 0) return;

        double speedValue = speed.getValue().doubleValue();

        // Calculate movement direction
        if (forward != 0) {
            if (strafe > 0) {
                yaw += forward > 0 ? -45 : 45;
            } else if (strafe < 0) {
                yaw += forward > 0 ? 45 : -45;
            }
            strafe = 0;

            if (forward > 0) {
                forward = 1;
            } else if (forward < 0) {
                forward = -1;
            }
        }

        // Apply movement
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        Vec3 delta = player.getDeltaMovement();
        double motionX = forward * speedValue * -sin + strafe * speedValue * cos;
        double motionZ = forward * speedValue * cos + strafe * speedValue * sin;

        if (player.onGround()) {
            player.setDeltaMovement(motionX, delta.y, motionZ);
        } else {
            player.setDeltaMovement(motionX * 0.9, delta.y, motionZ * 0.9);
        }
    }

    private void boostSpeed() {
        // Boost speed - gives periodic speed boosts
        Player player = mc.player;
        tickCounter++;

        if (tickCounter >= 10) { // Boost every 10 ticks
            double speedValue = speed.getValue().doubleValue();

            if (player.onGround()) {
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(
                        look.x * speedValue,
                        player.getDeltaMovement().y,
                        look.z * speedValue
                );
            }

            tickCounter = 0;
        }
    }

    private void bhopSpeed() {
        // Bunny hop style speed
        Player player = mc.player;

        if (player.onGround() && player.zza > 0) {
            // Jump and apply speed
            player.jumpFromGround();

            double speedValue = speed.getValue().doubleValue();
            Vec3 look = player.getLookAngle();

            player.setDeltaMovement(
                    look.x * speedValue,
                    player.getDeltaMovement().y,
                    look.z * speedValue
            );
        }
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;

        // Remove speed effect if we added it
        if (effects.getValue() && mc.player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            mc.player.removeEffect(MobEffects.MOVEMENT_SPEED);
        }

        tickCounter = 0;
    }

    public enum Mode {
        VANILLA,
        STRAFE,
        BOOST,
        BHOP
    }
}
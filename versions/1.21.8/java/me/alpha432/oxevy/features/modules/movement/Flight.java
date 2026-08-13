package me.alpha432.oxevy.features.modules.movement;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class Flight extends Module {

    private final Setting<Mode> mode = mode("Mode", Mode.VANILLA);
    private final Setting<Float> speed = num("Speed", 0.5f, 0.1f, 5.0f);
    private final Setting<Float> verticalSpeed = num("VerticalSpeed", 0.5f, 0.1f, 3.0f);
    private final Setting<Boolean> glide = bool("Glide", false);
    private final Setting<Boolean> autoTakeoff = bool("AutoTakeoff", true);
    private final Setting<Boolean> timerBoost = bool("TimerBoost", false);
    private final Setting<Float> timerSpeed = num("TimerSpeed", 1.5f, 1.0f, 5.0f);
    private final Setting<Boolean> antiKick = bool("AntiKick", true);
    private final Setting<Integer> antiKickInterval = num("AntiKickInterval", 20, 1, 100);
    private final Setting<Boolean> damageBoost = bool("DamageBoost", false);

    private int antiKickCounter = 0;
    private double startY = 0;
    private boolean wasFlying = false;

    public Flight() {
        super("Flight", "Allows you to fly", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;

        startY = mc.player.getY();
        wasFlying = mc.player.getAbilities().flying;

        // Auto takeoff - give a small boost upward
        if (autoTakeoff.getValue() && mc.player.onGround()) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.5, mc.player.getDeltaMovement().z);
        }

        // Disable vanilla fly if we're using our own
        if (mode.getValue() != Mode.CREATIVE) {
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().mayfly = false;
        }

        antiKickCounter = 0;
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;

        // Restore vanilla flying state
        mc.player.getAbilities().flying = wasFlying;
        mc.player.getAbilities().mayfly = mc.player.isCreative();

        // Reset fall distance to prevent fall damage
        mc.player.fallDistance = 0;

        // Reset timer
        if (timerBoost.getValue()) {
            Oxevy.TIMER = 1.0f;
        }
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        // Check if jump key is pressed
        boolean jumping = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE) == 1;

        // Timer boost
        if (timerBoost.getValue()) {
            Oxevy.TIMER = timerSpeed.getValue();
        }

        // Apply flight based on mode
        switch (mode.getValue()) {
            case CREATIVE:
                creativeFlight();
                break;
            case VANILLA:
                vanillaFlight(jumping);
                break;
            case PACKET:
                packetFlight(jumping);
                break;
            case BOOST:
                boostFlight(jumping);
                break;
            case GLIDE:
                glideFlight(jumping);
                break;
        }

        // Anti-kick (prevents being kicked for flying)
        if (antiKick.getValue()) {
            antiKickCounter++;
            if (antiKickCounter >= antiKickInterval.getValue()) {
                antiKick();
                antiKickCounter = 0;
            }
        }

        // Damage boost - gain speed when taking damage
        if (damageBoost.getValue() && mc.player.hurtTime > 0) {
            Vec3 look = mc.player.getLookAngle();
            mc.player.setDeltaMovement(
                    look.x * speed.getValue() * 2,
                    mc.player.getDeltaMovement().y,
                    look.z * speed.getValue() * 2
            );
        }

        // Glide mode - slowly descend
        if (glide.getValue() && mode.getValue() != Mode.GLIDE) {
            if (mc.player.getDeltaMovement().y < -0.1) {
                mc.player.setDeltaMovement(
                        mc.player.getDeltaMovement().x,
                        -0.05,
                        mc.player.getDeltaMovement().z
                );
            }
        }
    }

    private void creativeFlight() {
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().mayfly = true;

        // Custom speed
        float flySpeed = speed.getValue() * 0.05f;
        mc.player.getAbilities().setFlyingSpeed(flySpeed);
    }

    private void vanillaFlight(boolean jumping) {
        // Simple motion-based flight
        mc.player.getAbilities().flying = false;
        mc.player.fallDistance = 0;

        double forward = mc.player.zza;
        double strafe = mc.player.xxa;
        float yaw = mc.player.getYRot();

        if (forward == 0 && strafe == 0 && !jumping && !mc.player.isShiftKeyDown()) {
            // Slow down when not moving
            mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x * 0.5,
                    mc.player.getDeltaMovement().y * 0.5,
                    mc.player.getDeltaMovement().z * 0.5
            );
            return;
        }

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

        // Apply horizontal movement
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        double motionX = forward * speedValue * -sin + strafe * speedValue * cos;
        double motionZ = forward * speedValue * cos + strafe * speedValue * sin;

        // Vertical movement
        double motionY = 0;
        if (jumping) {
            motionY = verticalSpeed.getValue().doubleValue();
        } else if (mc.player.isShiftKeyDown()) {
            motionY = -verticalSpeed.getValue().doubleValue();
        }

        mc.player.setDeltaMovement(motionX, motionY, motionZ);
    }

    private void packetFlight(boolean jumping) {
        // Flight using position packets
        mc.player.getAbilities().flying = false;
        mc.player.fallDistance = 0;

        double forward = mc.player.zza;
        double strafe = mc.player.xxa;
        float yaw = mc.player.getYRot();

        double speedValue = speed.getValue().doubleValue();

        // Calculate movement
        double motionX = 0, motionY = 0, motionZ = 0;

        if (forward != 0 || strafe != 0) {
            if (forward != 0) {
                if (strafe > 0) {
                    yaw += forward > 0 ? -45 : 45;
                } else if (strafe < 0) {
                    yaw += forward > 0 ? 45 : -45;
                }
                strafe = 0;
            }

            double radians = Math.toRadians(yaw);
            motionX = forward * speedValue * -Math.sin(radians);
            motionZ = forward * speedValue * Math.cos(radians);
        }

        // Vertical
        if (jumping) {
            motionY = verticalSpeed.getValue().doubleValue();
        } else if (mc.player.isShiftKeyDown()) {
            motionY = -verticalSpeed.getValue().doubleValue();
        }

        // Apply movement
        mc.player.setDeltaMovement(motionX, motionY, motionZ);
    }

    private void boostFlight(boolean jumping) {
        // Boost-style flight (like creative but with bursts)
        mc.player.getAbilities().flying = false;
        mc.player.fallDistance = 0;

        double forward = mc.player.zza;
        double strafe = mc.player.xxa;

        if (forward != 0 || strafe != 0 || jumping || mc.player.isShiftKeyDown()) {
            // Boost when moving
            Vec3 look = mc.player.getLookAngle();

            double motionX = look.x * speed.getValue().doubleValue();
            double motionY = 0;
            double motionZ = look.z * speed.getValue().doubleValue();

            if (jumping) {
                motionY = verticalSpeed.getValue().doubleValue();
            } else if (mc.player.isShiftKeyDown()) {
                motionY = -verticalSpeed.getValue().doubleValue();
            }

            mc.player.setDeltaMovement(motionX, motionY, motionZ);
        } else {
            // Slow down when not moving
            mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x * 0.7,
                    mc.player.getDeltaMovement().y * 0.7,
                    mc.player.getDeltaMovement().z * 0.7
            );
        }
    }

    private void glideFlight(boolean jumping) {
        // Glide mode - like elytra but without elytra
        mc.player.getAbilities().flying = false;
        mc.player.fallDistance = 0;

        if (mc.player.getDeltaMovement().y < -0.5) {
            mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x,
                    -0.1,
                    mc.player.getDeltaMovement().z
            );
        }

        // Horizontal movement while gliding
        if (mc.player.zza != 0) {
            Vec3 look = mc.player.getLookAngle();
            double speedValue = speed.getValue().doubleValue();

            mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x + look.x * speedValue * 0.1,
                    mc.player.getDeltaMovement().y,
                    mc.player.getDeltaMovement().z + look.z * speedValue * 0.1
            );
        }

        // Ascend with jump
        if (jumping) {
            mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x,
                    0.3,
                    mc.player.getDeltaMovement().z
            );
        }
    }

    private void antiKick() {
        // Small downward movement to prevent anti-cheat kicks
        if (!mc.player.onGround()) {
            mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x,
                    mc.player.getDeltaMovement().y - 0.08,
                    mc.player.getDeltaMovement().z
            );
        }
    }

    public enum Mode {
        CREATIVE,   // Uses vanilla creative flight
        VANILLA,    // Motion-based flight
        PACKET,     // Packet-spoofing flight
        BOOST,      // Boost-style flight
        GLIDE       // Gliding flight
    }
}
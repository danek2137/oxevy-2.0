package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.entity.player.TickEvent;
import me.alpha432.oxevy.event.impl.network.PacketEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * AimBot module with optimized reflection caching.
 * Field lookups are cached statically to avoid repeated reflection overhead.
 */
public class AimBotModule extends Module {
    // Cached reflection fields - initialized once at class load
    private static final Field[] YAW_FIELDS = cacheFields(ServerboundMovePlayerPacket.class, 
        new String[]{"yRot", "yaw", "rotationYaw", "field_12920"});
    private static final Field[] PITCH_FIELDS = cacheFields(ServerboundMovePlayerPacket.class,
        new String[]{"xRot", "pitch", "rotationPitch", "field_12921"});

    private static Field[] cacheFields(Class<?> clazz, String[] fieldNames) {
        Field[] fields = new Field[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            try {
                fields[i] = clazz.getDeclaredField(fieldNames[i]);
                fields[i].setAccessible(true);
            } catch (NoSuchFieldException e) {
                fields[i] = null;
            }
        }
        return fields;
    }

    private final Setting<Double> speed = num("Speed", 5.0, 0.1, 10.0);
    private final Setting<Boolean> silent = bool("Silent", true);
    private final Setting<Double> fov = num("FOV", 90.0, 0.0, 180.0);
    private final Setting<Boolean> prediction = bool("Prediction", true);
    private final Setting<Boolean> teams = bool("Teams", false);
    private final Setting<Boolean> invisibles = bool("Invisibles", true);
    private final Setting<Boolean> randomization = bool("Randomization", false);

    private Entity target = null;

    public AimBotModule() {
        super("AimBot", "Automatically aims at entities.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        target = findTarget();
        
        if (target != null && !silent.getValue()) {
            rotateTo(target);
        }
    }

    @Subscribe
    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && target != null && silent.getValue()) {
            if (packet.hasRotation()) {
                float[] angle = getRotations(target);
                try {
                    setPacketRotation(packet, angle[0], angle[1]);
                } catch (Exception e) {
                    // Fail silently
                }
            }
        }
    }

    /**
     * Optimized packet rotation setting using cached Field objects.
     * Avoids repeated reflection lookups.
     */
    private void setPacketRotation(ServerboundMovePlayerPacket packet, float yaw, float pitch) throws Exception {
        // Use cached yaw field
        for (Field yawField : YAW_FIELDS) {
            if (yawField != null) {
                yawField.setFloat(packet, yaw);
                break;
            }
        }
        
        // Use cached pitch field
        for (Field pitchField : PITCH_FIELDS) {
            if (pitchField != null) {
                pitchField.setFloat(packet, pitch);
                break;
            }
        }
    }

    private Entity findTarget() {
        Entity closestEntity = null;
        double closestDiff = Double.MAX_VALUE;

        // Optimized for-loop instead of Stream API
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isValid(entity)) continue;

            double diff = getRotationDifference(entity);
            if (diff < closestDiff) {
                closestDiff = diff;
                closestEntity = entity;
            }
        }

        return closestEntity;
    }

    private boolean isValid(Entity entity) {
        if (entity == null || entity.equals(mc.player)) return false;
        if (me.alpha432.oxevy.util.entity.GhostPlayerEntity.isGhost(entity)) return false;
        if (!entity.isAlive()) return false;
        if (!invisibles.getValue() && entity.isInvisible()) return false;
        
        if (getRotationDifference(entity) > fov.getValue() / 2.0) return false;

        if (entity instanceof Player) {
            if (Oxevy.friendManager.isFriend((Player) entity)) return false;
            if (!teams.getValue() && mc.player.isAlliedTo(entity)) return false;
        } else {
             if (!(entity instanceof LivingEntity)) return false;
        }
        return true;
    }

    private double getRotationDifference(Entity entity) {
        float[] angle = getRotations(entity);
        return Math.abs(MathUtil.wrapDegrees(angle[0] - mc.player.getYRot())) + 
               Math.abs(MathUtil.wrapDegrees(angle[1] - mc.player.getXRot()));
    }
    
    private float[] getRotations(Entity entity) {
        Vec3 targetPos = entity.getEyePosition();
        if (prediction.getValue()) {
            targetPos = targetPos.add(entity.getDeltaMovement());
        }
        if (randomization.getValue()) {
             targetPos = targetPos.add(MathUtil.getRandom(-0.1, 0.1), MathUtil.getRandom(-0.1, 0.1), MathUtil.getRandom(-0.1, 0.1));
        }
        return MathUtil.calcAngle(mc.player.getEyePosition(), targetPos);
    }

    private void rotateTo(Entity entity) {
        float[] angle = getRotations(entity);
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        
        float yawDelta = MathUtil.wrapDegrees(angle[0] - currentYaw);
        float pitchDelta = MathUtil.wrapDegrees(angle[1] - currentPitch);
        
        float factor = speed.getValue().floatValue() / 10.0f;
        if (factor > 1.0f) factor = 1.0f;

        float newYaw = currentYaw + (yawDelta * factor);
        float newPitch = currentPitch + (pitchDelta * factor);
        
        Oxevy.rotationManager.setPlayerRotations(newYaw, newPitch);
    }
}

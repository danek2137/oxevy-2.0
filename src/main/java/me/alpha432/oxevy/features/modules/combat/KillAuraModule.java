package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.MathUtil;
import me.alpha432.oxevy.util.render.RenderUtil;
import me.alpha432.oxevy.util.render.gl.TargetCircleRenderer;
import me.alpha432.oxevy.util.render.gl.TargetVisualRenderer;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.awt.Color;

public class KillAuraModule extends Module {
    private final Setting<Double> range = num("Range", 5.0, 0.1, 10.0);
    private final Setting<Double> cps = num("CPS", 10.0, 1.0, 20.0);
    private final Setting<Boolean> randomization = bool("Randomization", true);
    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Boolean> smooth = bool("Smooth", true);
    private final Setting<Float> smoothSpeed = num("SmoothSpeed", 90.0f, 10.0f, 360.0f);
    private final Setting<Boolean> silentSwing = bool("SilentSwing", false);
    private final Setting<Boolean> prediction = bool("Prediction", false);
    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> mobs = bool("Mobs", true);
    private final Setting<Boolean> animals = bool("Animals", false);
    private final Setting<Boolean> vehicles = bool("Vehicles", false);
    private final Setting<Boolean> projectiles = bool("Projectiles", false);
    private final Setting<Boolean> teams = bool("Teams", false);
    private final Setting<Boolean> invisibles = bool("Invisibles", true);
    private final Setting<Boolean> syncWeapon = bool("SyncWeapon", false);

    public enum VisualMode {
        CIRCLE,
        GLOW,
        BEAM
    }

    private final Setting<VisualMode> visual = mode("Visual", VisualMode.CIRCLE);
    private final Setting<Color> targetColor = color("TargetColor", 255, 50, 50, 200);

    private long lastAttackTime = 0;
    private Entity target = null;
    private long startTime = System.currentTimeMillis();

    private float smoothYaw;
    private float smoothPitch;
    private boolean smoothingInit = false;

    public KillAuraModule() {
        super("KillAura", "Automatically attacks entities.", Category.COMBAT);
        smoothSpeed.setVisibility(v -> smooth.getValue());
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;

        target = findTarget();
        Oxevy.targetManager.setTarget(target instanceof LivingEntity le ? le : null);
        if (target != null) {
            if (rotate.getValue()) {
                rotateTo(target, 0f);
            }
            if (shouldAttack()) {
                attack(target);
            }
        } else {
            smoothingInit = false;
        }
    }

    @Override
    public void onDisable() {
        Oxevy.targetManager.clearTarget();
        smoothingInit = false;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null || target == null) return;

        if (rotate.getValue()) {
            rotateTo(target, event.getDelta());
        }

        Color color = targetColor.getValue();

        switch (visual.getValue()) {
            case CIRCLE:
                if (target instanceof LivingEntity living) {
                    float time = (System.currentTimeMillis() - startTime) / 1000f;
                    TargetCircleRenderer.drawRing(event.getMatrix(), living, event.getDelta(), time, color.getRGB(), true);
                }
                break;
            case GLOW:
                if (target instanceof LivingEntity living) {
                    float time = (System.currentTimeMillis() - startTime) / 1000f;
                    TargetVisualRenderer.drawGlowRing(event.getMatrix(), living, event.getDelta(), time, color.getRGB(), true);
                }
                break;
            case BEAM:
                if (target instanceof LivingEntity living) {
                    float time = (System.currentTimeMillis() - startTime) / 1000f;
                    TargetVisualRenderer.drawBeam(event.getMatrix(), living, event.getDelta(), time, color.getRGB(), true);
                }
                break;
        }
    }

    private AABB getLerpedBox(Entity entity, float delta) {
        double x = Mth.lerp(delta, entity.xo, entity.getX());
        double y = Mth.lerp(delta, entity.yo, entity.getY());
        double z = Mth.lerp(delta, entity.zo, entity.getZ());
        double w = entity.getBbWidth() / 2;
        double h = entity.getBbHeight();
        return new AABB(x - w, y, z - w, x + w, y + h, z + w);
    }

    private Entity findTarget() {
        Entity closestEntity = null;
        double closestDistanceSq = Double.MAX_VALUE;
        double rangeSq = range.getValue() * range.getValue();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isValid(entity)) continue;

            double distanceSq = mc.player.distanceToSqr(entity);
            if (distanceSq < closestDistanceSq && distanceSq <= rangeSq) {
                closestDistanceSq = distanceSq;
                closestEntity = entity;
            }
        }

        return closestEntity;
    }

    private boolean isValid(Entity entity) {
        if (entity == null || entity.equals(mc.player)) return false;
        if (mc.player.distanceToSqr(entity) > range.getValue() * range.getValue()) return false;
        if (entity.isRemoved() || !entity.isAlive()) return false;
        if (!invisibles.getValue() && entity.isInvisible()) return false;
        if (entity instanceof Player) {
            if (!players.getValue()) return false;
            if (Oxevy.friendManager.isFriend((Player) entity)) return false;
            if (!teams.getValue() && isTeammate((Player) entity)) return false;
        } else if (entity instanceof Monster || entity instanceof Mob) {
            if (!mobs.getValue()) return false;
        } else if (entity instanceof Animal) {
            if (!animals.getValue()) return false;
        } else if (entity instanceof VehicleEntity) {
            if (!vehicles.getValue()) return false;
        } else if (entity instanceof Projectile) {
            if (!projectiles.getValue()) return false;
        } else {
             if (!(entity instanceof LivingEntity)) return false;
        }
        return true;
    }

    private boolean isTeammate(Player player) {
        return mc.player.isAlliedTo(player);
    }

    private void rotateTo(Entity entity, float delta) {
        Vec3 targetPos = entity.getEyePosition();
        if (prediction.getValue()) {
            targetPos = targetPos.add(entity.getDeltaMovement());
        }
        float[] angle = MathUtil.calcAngle(mc.player.getEyePosition(), targetPos);

        if (smooth.getValue()) {
            if (!smoothingInit) {
                smoothYaw = mc.player.getYRot();
                smoothPitch = mc.player.getXRot();
                smoothingInit = true;
            }

            float yawDelta = MathUtil.wrapDegrees(angle[0] - smoothYaw);
            float pitchDelta = MathUtil.wrapDegrees(angle[1] - smoothPitch);

            float maxStep = smoothSpeed.getValue() / 20.0f * delta;
            smoothYaw += Mth.clamp(yawDelta, -maxStep, maxStep);
            smoothPitch += Mth.clamp(pitchDelta, -maxStep, maxStep);

            Oxevy.rotationManager.setPlayerRotations(smoothYaw, smoothPitch);
        } else {
            smoothingInit = false;
            Oxevy.rotationManager.setPlayerRotations(angle[0], angle[1]);
        }
    }

    private boolean shouldAttack() {
        if (syncWeapon.getValue()) {
            double attackSpeed = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
            if (attackSpeed <= 0) attackSpeed = 4.0;
            long delay = (long) (1000.0 / attackSpeed);
            return System.currentTimeMillis() - lastAttackTime >= delay;
        }
        long timeSinceLastAttack = System.currentTimeMillis() - lastAttackTime;
        double currentCps = cps.getValue();
        if (randomization.getValue()) {
            currentCps += MathUtil.getRandom(-2.0, 2.0);
        }
        if (currentCps <= 0) currentCps = 1;
        long delay = (long) (1000.0 / currentCps);
        return timeSinceLastAttack >= delay;
    }

    private void attack(Entity entity) {
        mc.gameMode.attack(mc.player, entity);
        if (entity instanceof LivingEntity living) {
            Oxevy.targetManager.recordHit(living);
        }
        if (silentSwing.getValue()) {
            mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        } else {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        lastAttackTime = System.currentTimeMillis();
    }
}

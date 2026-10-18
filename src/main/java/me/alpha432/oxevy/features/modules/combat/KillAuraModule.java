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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class KillAuraModule extends Module {
    public enum AttackMode {
        /** Attacks straight through MultiPlayerGameMode#attack. */
        PACKET,
        /** Attacks through vanilla's real left-click path (Minecraft#startAttack). */
        CLICK
    }

    private final Setting<AttackMode> mode = mode("Mode", AttackMode.PACKET);
    private final Setting<Double> range = num("Range", 5.0, 0.1, 10.0);
    private final Setting<Double> cps = num("CPS", 10.0, 1.0, 20.0);
    private final Setting<Boolean> randomization = bool("Randomization", true);
    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Boolean> smooth = bool("Smooth", true);
    private final Setting<Float> smoothSpeed = num("SmoothSpeed", 360.0f, 10.0f, 720.0f);
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
    private final Setting<Double> fov = num("FOV", 360.0, 30.0, 360.0);
    private final Setting<Integer> maxTargets = num("MaxTargets", 1, 1, 5);

    public enum PriorityMode {
        DISTANCE,
        HEALTH,
        ARMOR,
        HURT,
        RANDOM
    }

    private final Setting<PriorityMode> priority = mode("Priority", PriorityMode.DISTANCE);

    public enum VisualMode {
        CIRCLE,
        GLOW,
        BEAM
    }

    private final Setting<VisualMode> visual = mode("Visual", VisualMode.CIRCLE);
    private final Setting<Color> targetColor = color("TargetColor", 255, 50, 50, 200);

    private long lastAttackTime = 0;
    private final List<Entity> targets = new ArrayList<>();
    private long startTime = System.currentTimeMillis();

    private float smoothYaw;
    private float smoothPitch;
    private boolean smoothingInit = false;
    private long lastRotationUpdateNs = 0;

    public KillAuraModule() {
        super("KillAura", "Automatically attacks entities.", Category.COMBAT);
        smoothSpeed.setVisibility(v -> smooth.getValue());
        // A click already swings through vanilla's own attack path, so the
        // silent-swing packet would just duplicate it.
        silentSwing.setVisibility(v -> mode.getValue() == AttackMode.PACKET);
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;

        if (AutoBlockModule.isBlocking()) {
            targets.clear();
            smoothingInit = false;
            return;
        }

        targets.clear();
        List<Entity> found = findTargets();
        for (Entity entity : found) {
            if (isInView(entity)) targets.add(entity);
        }
        Oxevy.targetManager.setTarget(!targets.isEmpty() && targets.get(0) instanceof LivingEntity le ? le : null);

        if (!targets.isEmpty()) {
            if (rotate.getValue()) {
                rotateTo(targets.get(0));
            }
            if (shouldAttack()) {
                if (mode.getValue() == AttackMode.CLICK) {
                    // One click can only land on whatever the crosshair is
                    // resting on, so only the primary target is reachable.
                    clickAttack(targets.get(0));
                } else {
                    for (Entity t : targets) {
                        attack(t);
                    }
                }
            }
        } else {
            smoothingInit = false;
        }
    }

    @Override
    public void onEnable() {
        targets.clear();
        smoothingInit = false;
        lastRotationUpdateNs = 0;
    }

    @Override
    public void onDisable() {
        Oxevy.targetManager.clearTarget();
        targets.clear();
        smoothingInit = false;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null || targets.isEmpty()) return;

        if (rotate.getValue()) {
            rotateTo(targets.get(0));
        }

        Color color = targetColor.getValue();

        for (Entity target : targets) {
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
    }

    private AABB getLerpedBox(Entity entity, float delta) {
        double x = Mth.lerp(delta, entity.xo, entity.getX());
        double y = Mth.lerp(delta, entity.yo, entity.getY());
        double z = Mth.lerp(delta, entity.zo, entity.getZ());
        double w = entity.getBbWidth() / 2;
        double h = entity.getBbHeight();
        return new AABB(x - w, y, z - w, x + w, y + h, z + w);
    }

    private List<Entity> findTargets() {
        List<Entity> candidates = new ArrayList<>();
        double rangeSq = range.getValue() * range.getValue();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isValid(entity)) continue;
            if (mc.player.distanceToSqr(entity) > rangeSq) continue;
            candidates.add(entity);
        }

        if (candidates.isEmpty()) return List.of();
        if (priority.getValue() == PriorityMode.RANDOM) {
            java.util.Collections.shuffle(candidates);
            return candidates.subList(0, Math.min(maxTargets.getValue(), candidates.size()));
        }

        candidates.sort((a, b) -> {
            return better(a, b) ? -1 : 1;
        });
        return candidates.subList(0, Math.min(maxTargets.getValue(), candidates.size()));
    }

    private boolean better(Entity candidate, Entity current) {
        if (current == null) return true;
        return switch (priority.getValue()) {
            case HEALTH -> healthOf(candidate) < healthOf(current);
            case ARMOR -> armorOf(candidate) < armorOf(current);
            case HURT -> hurtTimeOf(candidate) > hurtTimeOf(current);
            default -> mc.player.distanceToSqr(candidate) < mc.player.distanceToSqr(current);
        };
    }

    private float healthOf(Entity entity) {
        return entity instanceof LivingEntity living ? living.getHealth() : Float.MAX_VALUE;
    }

    private int armorOf(Entity entity) {
        return entity instanceof LivingEntity living ? living.getArmorValue() : Integer.MAX_VALUE;
    }

    private int hurtTimeOf(Entity entity) {
        return entity instanceof LivingEntity living ? living.hurtTime : 0;
    }

    private boolean isValid(Entity entity) {
        if (entity == null || entity.equals(mc.player)) return false;
        if (me.alpha432.oxevy.util.entity.GhostPlayerEntity.isGhost(entity)) return false;
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

    private boolean isInView(Entity entity) {
        float[] angle = MathUtil.calcAngle(mc.player.getEyePosition(), entity.getEyePosition());
        float yawDiff = Math.abs(MathUtil.wrapDegrees(angle[0] - mc.player.getYRot()));
        float pitchDiff = Math.abs(angle[1] - mc.player.getXRot());
        return yawDiff <= fov.getValue() / 2.0 && pitchDiff <= fov.getValue() / 2.0;
    }

    private void rotateTo(Entity entity) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 targetPos = entity.getEyePosition();
        if (prediction.getValue()) {
            targetPos = targetPos.add(entity.getDeltaMovement());
        }
        float[] angle = MathUtil.calcAngle(eye, targetPos);

        if (smooth.getValue()) {
            if (!smoothingInit) {
                smoothYaw = mc.player.getYRot();
                smoothPitch = mc.player.getXRot();
                lastRotationUpdateNs = System.nanoTime();
                smoothingInit = true;
            }

            long now = System.nanoTime();
            float elapsed = Math.min((now - lastRotationUpdateNs) / 1_000_000_000f, 0.05f);
            lastRotationUpdateNs = now;

            // Exponential approach: never snaps to the target and never
            // overshoots. It also acts as a low-pass, so a jittery target
            // angle (entity bobbing around your eye height) doesn't make the
            // camera flicker up/down or left/right.
            float rate = smoothSpeed.getValue() / 20f;
            float factor = 1f - (float) Math.exp(-rate * elapsed);

            float yawDelta = MathUtil.wrapDegrees(angle[0] - smoothYaw);
            float pitchDelta = angle[1] - smoothPitch;

            // Near-vertical target: the yaw is undefined there and would spin
            // the camera, so hold the last yaw and only chase the pitch.
            double hDist = Math.sqrt(Math.pow(targetPos.x - eye.x, 2) + Math.pow(targetPos.z - eye.z, 2));
            if (hDist < 0.05) {
                yawDelta = 0f;
            }

            smoothYaw = Mth.wrapDegrees(smoothYaw + yawDelta * factor);
            smoothPitch = Mth.clamp(smoothPitch + pitchDelta * factor, -90f, 90f);

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

    /**
     * Attacks by handing the hit to vanilla's own left-click handler instead of
     * calling {@code MultiPlayerGameMode#attack} ourselves, so the swing, the
     * weapon cooldown reset and the miss-time bookkeeping all come from
     * {@code Minecraft#startAttack} rather than from this module.
     * <p>
     * That handler resolves the target from {@code Minecraft#hitResult}, the
     * raycast the game performs each tick from the player's look direction, so
     * we only click when the crosshair is genuinely resting on the target.
     * {@code hitResult} was produced by the previous {@code GameRenderer#pick},
     * i.e. from the rotations {@link #rotateTo} applied last tick - the same
     * thing a human click would have hit.
     */
    private void clickAttack(Entity entity) {
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == entity) {
            if (mc.player.isHandsBusy()) return;

            mc.startAttack();

            if (entity instanceof LivingEntity living) {
                Oxevy.targetManager.recordHit(living);
            }
            lastAttackTime = System.currentTimeMillis();
        }
    }
}

package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.MathUtil;
import me.alpha432.oxevy.util.render.gl.TargetCircleRenderer;
import me.alpha432.oxevy.util.render.gl.TargetVisualRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.awt.Color;

public class KomarModule extends Module {

    public enum VisualMode {
        CIRCLE,
        GLOW,
        BEAM
    }

    private final Setting<Double> range = num("Range", 80.0, 10.0, 300.0);
    private final Setting<Double> attackRange = num("AttackRange", 4.5, 1.0, 8.0);
    private final Setting<Double> cps = num("CPS", 8.0, 1.0, 20.0);
    private final Setting<Boolean> autoStart = bool("AutoStart", true);
    private final Setting<Double> heightOffset = num("HeightOffset", 0.0, -10.0, 10.0);

    private final Setting<VisualMode> visual = mode("Visual", VisualMode.CIRCLE);

    private final Setting<Color> targetColor = color("TargetColor", 255, 50, 50, 200);

    private Player target;
    private long lastAttackTime = 0;
    private boolean wasJumpDown = false;
    private long startTime = System.currentTimeMillis();

    public KomarModule() {
        super("Komar", "Follows and attacks players with elytra", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;

        target = findTarget();
        Oxevy.targetManager.setTarget(target);

        if (target == null) {
            if (wasJumpDown) {
                mc.options.keyJump.setDown(false);
                wasJumpDown = false;
            }
            return;
        }

        if (autoStart.getValue() && hasElytra() && !mc.player.isFallFlying() && !mc.player.onGround()) {
            mc.options.keyJump.setDown(true);
            wasJumpDown = true;
        } else if (mc.player.isFallFlying() && wasJumpDown) {
            mc.options.keyJump.setDown(false);
            wasJumpDown = false;
        }

        if (mc.player.isFallFlying()) {
            flyTowardTarget();
        }

        if (mc.player.distanceToSqr(target) <= attackRange.getValue() * attackRange.getValue()) {
            if (shouldAttack()) {
                attack(target);
            }
        }
    }

    @Override
    public void onDisable() {
        Oxevy.targetManager.clearTarget();
        if (wasJumpDown) {
            mc.options.keyJump.setDown(false);
            wasJumpDown = false;
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null || target == null) return;

        Color color = targetColor.getValue();

        renderVisual(event, target, color.getRGB());

        if (mc.player.distanceToSqr(target) <= attackRange.getValue() * attackRange.getValue()) {
            renderVisual(event, target, new Color(50, 255, 50, 200).getRGB());
        }
    }

    private void renderVisual(Render3DEvent event, Player player, int rgba) {
        float time = (System.currentTimeMillis() - startTime) / 1000f;
        float delta = event.getDelta();

        switch (visual.getValue()) {
            case CIRCLE:
                TargetCircleRenderer.drawRing(event.getMatrix(), player, delta, time, rgba, true);
                break;
            case GLOW:
                TargetVisualRenderer.drawGlowRing(event.getMatrix(), player, delta, time, rgba, true);
                break;
            case BEAM:
                TargetVisualRenderer.drawBeam(event.getMatrix(), player, delta, time, rgba, true);
                break;
        }
    }

    private Player findTarget() {
        Player closest = null;
        double closestDistSq = Double.MAX_VALUE;
        double rangeSq = range.getValue() * range.getValue();

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player)) continue;
            if (me.alpha432.oxevy.util.entity.GhostPlayerEntity.isGhost(player)) continue;
            if (player == mc.player) continue;
            if (!player.isAlive() || player.isRemoved()) continue;
            if (Oxevy.friendManager.isFriend(player)) continue;

            double distSq = mc.player.distanceToSqr(player);
            if (distSq < closestDistSq && distSq <= rangeSq) {
                closestDistSq = distSq;
                closest = player;
            }
        }
        return closest;
    }

    private boolean hasElytra() {
        return mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private void flyTowardTarget() {
        Vec3 targetPos = target.getEyePosition().add(0, heightOffset.getValue(), 0);
        float[] angle = MathUtil.calcAngle(mc.player.getEyePosition(), targetPos);
        Oxevy.rotationManager.setPlayerRotations(angle[0], angle[1]);
    }

    private boolean shouldAttack() {
        long elapsed = System.currentTimeMillis() - lastAttackTime;
        long delay = (long) (1000.0 / cps.getValue());
        return elapsed >= delay;
    }

    private void attack(Player target) {
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        Oxevy.targetManager.recordHit(target);
        lastAttackTime = System.currentTimeMillis();
    }

    @Override
    public String getDisplayInfo() {
        return target != null ? target.getGameProfile().name() : null;
    }
}

package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;

public class AnimationsModule extends Module {

    public enum Mode {
        Vanilla,
        Old,
        Smooth,
        Big,
        Slide
    }

    public final Setting<Mode> mode = mode("Mode", Mode.Vanilla);
    public final Setting<Double> speed = num("Speed", 1.0, 0.5, 4.0);
    public final Setting<Double> arc = num("Arc", 1.0, 0.25, 2.5);
    public final Setting<Boolean> mainHandOnly = bool("Main Hand Only", true);
    public final Setting<Boolean> mainHand = bool("Main Hand", true);
    public final Setting<Boolean> offHand = bool("Off Hand", true);

    public AnimationsModule() {
        super("Animations", "Changes the speed and look of hand animations (mining/attacking)", Category.RENDER);
    }

    public boolean shouldApply() {
        if (!isEnabled()) return false;
        if (speed.getValue() != 1.0) return true;
        return mode.getValue() != Mode.Vanilla;
    }

    /**
     * Compute the custom swing transform for the given arm and swing progress.
     * Returns an array of {rotX, rotY, rotZ} in degrees plus translation, or null
     * to keep the vanilla swing.
     */
    public SwingPose computeSwing(HumanoidArm arm, float progress) {
        Mode m = mode.getValue();
        float speedMul = speed.getValue().floatValue();

        float dir = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        float sp = clamp(progress * speedMul, 0.0f, 1.0f);
        float a = arc.getValue().floatValue();

        // Vanilla look, but scaled by the speed multiplier.
        if (m == Mode.Vanilla) {
            if (speedMul == 1.0f) return null;
            return vanillaPose(dir, sp, a);
        }

        float rx, ry, rz;
        switch (m) {
            case Old:
                rx = -80.0f * quad(sp) * a * dir;
                ry = 45.0f * quad(sp) * a * dir;
                rz = -20.0f * quad(sp) * a * dir;
                break;
            case Smooth:
                float s = sin(sp * 3.1415927f);
                rx = -115.0f * s * a * dir;
                ry = 45.0f * s * a * dir;
                rz = -25.0f * s * a * dir;
                break;
            case Big:
                float big = sqrt(sp);
                rx = -160.0f * big * a * dir;
                ry = 65.0f * big * a * dir;
                rz = -40.0f * big * a * dir;
                break;
            case Slide:
                rx = -25.0f * sin(sp * 3.1415927f) * dir;
                ry = -15.0f * dir;
                rz = 0.0f;
                return new SwingPose(rx, ry, rz, 0.55f * (sp - 0.5f) * 2.0f * dir, 0.0f, -0.1f * sp * dir);
            default:
                return null;
        }
        return new SwingPose(rx, ry, rz, 0.0f, 0.0f, 0.0f);
    }

    // Reproduce the vanilla swing arm rotation so Speed works without changing the look.
    private SwingPose vanillaPose(float dir, float sp, float a) {
        float f5_rot = sin(sp * sp * 3.1415927f);
        float f6_rot = sin(sqrt(sp) * 3.1415927f);
        float rotY = dir * (45.0f + f5_rot * -20.0f) * a;
        float rotZ = dir * f6_rot * -20.0f * a;
        float rotX = f6_rot * -80.0f * a;
        return new SwingPose(rotX, rotY, rotZ, 0.0f, 0.0f, 0.0f);
    }

    /** Transform the pose stack with the computed swing. */
    public void apply(PoseStack matrices, HumanoidArm arm, float progress) {
        SwingPose p = computeSwing(arm, progress);
        if (p == null) return;
        if (p.tx != 0 || p.ty != 0 || p.tz != 0)
            matrices.translate(p.tx, p.ty, p.tz);
        if (p.rotX != 0) matrices.mulPose(Axis.XP.rotationDegrees(p.rotX));
        if (p.rotY != 0) matrices.mulPose(Axis.YP.rotationDegrees(p.rotY));
        if (p.rotZ != 0) matrices.mulPose(Axis.ZP.rotationDegrees(p.rotZ));
    }

    public static class SwingPose {
        public final float rotX, rotY, rotZ, tx, ty, tz;
        public SwingPose(float rx, float ry, float rz, float tx, float ty, float tz) {
            this.rotX = rx; this.rotY = ry; this.rotZ = rz;
            this.tx = tx; this.ty = ty; this.tz = tz;
        }
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
    private static float quad(float v) { return v * v; }
    private static float sqrt(float v) { return (float) Math.sqrt(v); }
    private static float sin(float v) { return (float) Math.sin(v); }
}

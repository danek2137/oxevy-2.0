package me.alpha432.oxevy.features.modules.movement;

import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class FreeCam extends Module {

    private final Setting<Double> speed = num("Speed", 1.0, 0.05, 10.0);
    private final Setting<Double> scrollSensitivity = num("ScrollSensitivity", 0.25, 0.0, 2.0);
    private final Setting<Boolean> toggleOnDamage = bool("ToggleOnDamage", true);
    private final Setting<Boolean> toggleOnDeath = bool("ToggleOnDeath", false);
    private final Setting<Boolean> toggleOnLog = bool("ToggleOnLog", true);
    private final Setting<Boolean> reloadChunks = bool("ReloadChunks", true);
    private final Setting<Boolean> showHands = bool("ShowHands", false);
    private final Setting<Boolean> lookAtPlayer = bool("LookAtPlayer", false);
    private final Setting<Boolean> tracer = bool("Tracer", false);

    private Vec3 pos;
    private Vec3 prevPos;
    private Vec3 frozenPos;
    private float yaw;
    private float pitch;
    private double speedValue;
    private CameraType prevPerspective;
    private boolean forward, backward, right, left, up, down;
    private float lastHealth;

    public FreeCam() {
        super("FreeCam", "Client-side free camera that detaches from your body", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;

        LocalPlayer player = mc.player;
        pos = player.getEyePosition();
        prevPos = pos;
        frozenPos = player.position();
        yaw = player.getYRot();
        pitch = player.getXRot();
        speedValue = speed.getValue();
        lastHealth = player.getHealth();
        prevPerspective = mc.options.getCameraType();

        if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            yaw += 180;
            pitch *= -1;
        }

        unpress();

        if (reloadChunks.getValue()) mc.levelRenderer.allChanged();
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;

        if (reloadChunks.getValue()) {
            mc.execute(mc.levelRenderer::allChanged);
        }

        mc.player.setNoGravity(false);
        mc.options.setCameraType(prevPerspective);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        LocalPlayer player = mc.player;

        if (pos == null) {
            pos = player.getEyePosition();
            prevPos = pos;
            frozenPos = player.position();
            yaw = player.getYRot();
            pitch = player.getXRot();
        }

        if (player.isDeadOrDying() && toggleOnDeath.getValue()) {
            toggle();
            Command.sendMessage("FreeCam toggled off because you died.");
            return;
        }

        float health = player.getHealth();
        if (toggleOnDamage.getValue() && health < lastHealth) {
            toggle();
            Command.sendMessage("FreeCam toggled off because you took damage.");
            return;
        }
        lastHealth = health;

        // Freeze player body in place
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        if (frozenPos != null) {
            player.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
        }

        if (!mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);

        long handle = mc.getWindow().getWindow();
        forward = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        backward = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        left = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        right = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
        up = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        down = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;

        if (mc.screen != null && !forward && !backward && !right && !left && !up && !down) {
            prevPos = pos;
            return;
        }

        Vec3 forwardVec = Vec3.directionFromRotation(0, yaw);
        Vec3 rightVec = Vec3.directionFromRotation(0, yaw + 90);
        double velX = 0, velY = 0, velZ = 0;

        double s = 0.5;
        if (GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) s = 1.0;

        boolean hasForward = false;
        if (forward) {
            velX += forwardVec.x * s * speedValue;
            velZ += forwardVec.z * s * speedValue;
            hasForward = true;
        }
        if (backward) {
            velX -= forwardVec.x * s * speedValue;
            velZ -= forwardVec.z * s * speedValue;
            hasForward = true;
        }

        boolean hasStrafe = false;
        if (right) {
            velX += rightVec.x * s * speedValue;
            velZ += rightVec.z * s * speedValue;
            hasStrafe = true;
        }
        if (left) {
            velX -= rightVec.x * s * speedValue;
            velZ -= rightVec.z * s * speedValue;
            hasStrafe = true;
        }

        if (hasForward && hasStrafe) {
            double diag = 1.0 / Math.sqrt(2);
            velX *= diag;
            velZ *= diag;
        }

        if (up) velY += s * speedValue;
        if (down) velY -= s * speedValue;

        prevPos = pos;
        pos = pos.add(velX, velY, velZ);

        if (lookAtPlayer.getValue()) {
            Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
            Vec3 dir = center.subtract(pos);
            yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            pitch = -(float) Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
            pitch = Mth.clamp(pitch, -90, 90);
        }
    }

    @Override
    public String getDisplayInfo() {
        return String.format("%.1f", speedValue);
    }

    public Vec3 getCamPos(float partialTicks) {
        if (pos == null || prevPos == null) return Vec3.ZERO;
        return prevPos.lerp(pos, partialTicks);
    }

    public float getCamYaw() { return yaw; }
    public float getCamPitch() { return pitch; }

    public Vec3 getScaledCamDir(double scale) {
        return Vec3.directionFromRotation(pitch, yaw).scale(scale);
    }

    public boolean shouldHideHand() {
        return isEnabled() && !showHands.getValue();
    }

    public void turn(double deltaYaw, double deltaPitch) {
        if (lookAtPlayer.getValue()) return;
        yaw += (float) deltaYaw;
        pitch += (float) deltaPitch;
        pitch = Mth.clamp(pitch, -90, 90);
    }

    public void onScroll(double delta) {
        if (scrollSensitivity.getValue() > 0) {
            speedValue += delta * 0.25 * scrollSensitivity.getValue() * speedValue;
            speedValue = Mth.clamp(speedValue, 0.1, 50.0);
        }
    }

    private void unpress() {
        long handle = mc.getWindow().getWindow();
        forward = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        backward = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        left = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        right = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
        up = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        down = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;

        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
    }
}

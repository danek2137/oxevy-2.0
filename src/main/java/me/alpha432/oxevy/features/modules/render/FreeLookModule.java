package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

public class FreeLookModule extends Module {

    public Setting<Mode> mode = mode("Mode", Mode.Camera);
    public Setting<Boolean> togglePerspective = bool("TogglePerspective", true);
    public Setting<Double> sensitivity = num("Sensitivity", 8.0, 0.0, 10.0);
    public Setting<Boolean> arrows = bool("ArrowKeys", true);
    public Setting<Double> arrowSpeed = num("ArrowSpeed", 4.0, 0.0, 10.0);

    public float cameraYaw;
    public float cameraPitch;

    private CameraType prePers;

    public FreeLookModule() {
        super("FreeLook", "Allows free camera rotation in third person", Category.RENDER);
    }

    @Override
    public void onEnable() {
        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();
        prePers = mc.options.getCameraType();
        if (prePers == null) prePers = CameraType.FIRST_PERSON;

        if (prePers != CameraType.THIRD_PERSON_BACK && togglePerspective.getValue()) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void onDisable() {
        if (mc.options.getCameraType() != null && mc.options.getCameraType() != prePers && togglePerspective.getValue()) {
            mc.options.setCameraType(prePers);
        } else if (prePers != null && togglePerspective.getValue()) {
            mc.options.setCameraType(prePers);
        }
    }

    public float getCamYaw() {
        return isEnabled() ? cameraYaw : mc.player.getYRot();
    }

    public float getCamPitch() {
        return isEnabled() ? cameraPitch : mc.player.getXRot();
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (arrows.getValue()) {
            long window = mc.getWindow().handle();
            float speed = (float) (arrowSpeed.getValue() * 0.5f);

            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS) cameraYaw -= speed;
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS) cameraYaw += speed;
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS) cameraPitch -= speed;
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS) cameraPitch += speed;
        }

        cameraPitch = Mth.clamp(cameraPitch, -90, 90);
    }

    public enum Mode {
        Player,
        Camera
    }
}

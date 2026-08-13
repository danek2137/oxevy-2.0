package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.CameraType;

import org.lwjgl.glfw.GLFW;

public class CameraTweaksModule extends Module {

    public Setting<Boolean> clip = bool("Clip", true);
    public Setting<Double> cameraDistance = num("CameraDistance", 4.0, 0.0, 50.0);
    public Setting<Boolean> scrolling = bool("Scrolling", true);
    public Setting<Double> scrollSensitivity = num("ScrollSensitivity", 1.0, 0.01, 5.0);

    public double distance;
    private boolean scrollKeyHeld = false;

    public CameraTweaksModule() {
        super("CameraTweaks", "Allows modification of third person camera", Category.RENDER);
    }

    @Override
    public void onEnable() {
        distance = cameraDistance.getValue();
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) return;

        if (scrolling.getValue()) {
            long window = mc.getWindow().getWindow();
            scrollKeyHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
        }
    }

    public boolean clip() {
        return isEnabled() && clip.getValue();
    }

    public double getDistance() {
        return isEnabled() ? distance : mc.options.getCameraType().isFirstPerson() ? 0 : 4.0;
    }

    public void adjustDistance(double delta) {
        if (!scrolling.getValue() || !isEnabled()) return;
        if (scrollKeyHeld || true) {
            if (scrollSensitivity.getValue() > 0) {
                distance -= delta * 0.25 * scrollSensitivity.getValue() * distance;
                distance = Math.max(1.0, distance);
            }
        }
    }
}

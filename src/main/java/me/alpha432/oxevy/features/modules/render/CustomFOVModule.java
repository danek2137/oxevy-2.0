package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;

/**
 * Allows setting the camera FOV beyond the vanilla cap (up to 170).
 */
public class CustomFOVModule extends Module {
    public Setting<Float> fov = num("FOV", 110.0f, 1.0f, 170.0f);

    public CustomFOVModule() {
        super("CustomFOV", "Set camera FOV beyond the vanilla 110 cap", Category.RENDER);
    }
}

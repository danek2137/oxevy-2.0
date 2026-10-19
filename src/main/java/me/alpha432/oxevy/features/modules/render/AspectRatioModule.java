package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;

/**
 * Forces a custom aspect ratio on the 3D projection (Soup Visuals / ThunderHack
 * style). Useful for streaming in 4:3, ultrawide, etc.
 */
public class AspectRatioModule extends Module {
    public Setting<Float> ratio = num("Ratio", 1.7778f, 0.1f, 5.0f);
    public Setting<Boolean> vertical = bool("Vertical", false);

    public AspectRatioModule() {
        super("AspectRatio", "Forces a custom aspect ratio (e.g. 4:3, 16:9, 21:9, or vertical)", Category.RENDER);
    }

    public float getAspect() {
        float r = ratio.getValue();
        return vertical.getValue() ? (1.0f / r) : r;
    }
}

package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.SkyEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import java.awt.Color;

/**
 * Custom sky + fog, reverse-engineered from Soup Visuals' technique:
 * a mixin fires a cancelable {@link SkyEvent} from the vanilla renderer and a
 * module subscribes to it, overriding sky color, fog color/distance and
 * sun/moon/star visibility.
 */
public class CustomSkyModule extends Module {

    public Setting<Boolean> rainbow = bool("Rainbow", true);
    public Setting<Color> skyColor = color("SkyColor", 80, 0, 200, 255);
    public Setting<Float> speed = num("Speed", 1.0f, 0.1f, 5.0f);
    public Setting<Boolean> hideSun = bool("HideSun", false);
    public Setting<Boolean> hideMoon = bool("HideMoon", false);
    public Setting<Boolean> hideStars = bool("HideStars", false);
    public Setting<Float> starDensity = num("StarDensity", 0.02f, 0.0f, 0.1f);
    public Setting<Float> starBrightness = num("StarBrightness", 0.8f, 0.0f, 2.0f);
    public Setting<Boolean> customFog = bool("CustomFog", false);
    public Setting<Color> fogColor = color("FogColor", 80, 0, 200, 255);
    public Setting<Float> fogDistance = num("FogDistance", 120.0f, 5.0f, 2000.0f);

    public CustomSkyModule() {
        super("CustomSky", "Custom sky color and fog (event-driven, Soup Visuals style)", Category.RENDER);
        fogColor.setVisibility(color -> customFog.getValue());
        fogDistance.setVisibility(distance -> customFog.getValue());
    }

    @Subscribe
    public void onSky(SkyEvent event) {
        if (!isEnabled()) {
            return;
        }

        int color;
        if (rainbow.getValue()) {
            long time = System.currentTimeMillis();
            float hue = (float) ((time * 0.0001 * speed.getValue()) % 1.0);
            Color c = Color.getHSBColor(hue, 0.8f, 1.0f);
            color = 0xFF000000 | (c.getRGB() & 0x00FFFFFF);
        } else {
            color = 0xFF000000 | (skyColor.getValue().getRGB() & 0x00FFFFFF);
        }

        event.setSkyColor(color);
        event.setHideSun(hideSun.getValue());
        event.setHideMoon(hideMoon.getValue());
        event.setHideStars(hideStars.getValue());

        if (customFog.getValue()) {
            event.setFogColor(0xFF000000 | (fogColor.getValue().getRGB() & 0x00FFFFFF));
            event.setFogDistance(fogDistance.getValue());
        }

        event.cancel();
    }

    public int getSkyColor() {
        if (rainbow.getValue()) {
            long time = System.currentTimeMillis();
            float hue = (float) ((time * 0.0001 * speed.getValue()) % 1.0);
            Color c = Color.getHSBColor(hue, 0.8f, 1.0f);
            return 0xFF000000 | (c.getRGB() & 0x00FFFFFF);
        }
        return 0xFF000000 | (skyColor.getValue().getRGB() & 0x00FFFFFF);
    }
}

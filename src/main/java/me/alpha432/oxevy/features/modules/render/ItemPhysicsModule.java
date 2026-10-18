package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;

public class ItemPhysicsModule extends Module {

    public Setting<Boolean> randomRotation = bool("RandomRotation", true);
    public Setting<Boolean> bobbing = bool("Bobbing", false);
    public Setting<Float> bobbingSpeed = num("BobbingSpeed", 0.5f, 0.1f, 3.0f);
    public Setting<Float> tumble = num("Tumble", 1.0f, 0.0f, 3.0f);
    public Setting<Float> bounce = num("Bounce", 1.0f, 0.0f, 2.0f);

    public ItemPhysicsModule() {
        super("ItemPhysics", "Dropped items tumble, bounce and settle with real physics", Category.RENDER);
    }
}

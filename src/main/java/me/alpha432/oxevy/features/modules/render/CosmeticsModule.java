package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.manager.SelfCosmeticsManager;

public class CosmeticsModule extends Module {

    public Setting<Float> checkDistance = num("CheckDistance", 16f, 8f, 64f);
    public Setting<Float> renderDistance = num("RenderDistance", 16f, 8f, 64f);

    private static CosmeticsModule instance;

    public CosmeticsModule() {
        super("Cosmetics", "Controls how far custom capes/skins are checked and rendered for nearby players. -oxevy network needed", Category.CLIENT);
        instance = this;
    }

    public static CosmeticsModule getInstance() {
        return instance;
    }

    public static float getCheckDistance() {
        return instance != null ? instance.checkDistance.getValue() : 16f;
    }

    public static float getRenderDistance() {
        return instance != null ? instance.renderDistance.getValue() : 16f;
    }

    @Override
    public void onEnable() {
        SelfCosmeticsManager.onModuleToggled();
    }

    @Override
    public void onDisable() {
        SelfCosmeticsManager.onModuleToggled();
    }
}

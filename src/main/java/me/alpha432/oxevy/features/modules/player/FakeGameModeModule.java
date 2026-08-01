package me.alpha432.oxevy.features.modules.player;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;

public class FakeGameModeModule extends Module {

    private final Setting<GameType> mode = mode("Mode", GameType.CREATIVE);
    private GameType previousGameMode = null;

    public FakeGameModeModule() {
        super("FakeGameMode", "Client-side fake gamemode", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        if (mc.gameMode != null) {
            previousGameMode = mc.gameMode.getPlayerMode();
            mc.gameMode.setLocalMode(mode.getValue());
        }
    }

    @Override
    public void onDisable() {
        if (mc.gameMode != null && previousGameMode != null) {
            mc.gameMode.setLocalMode(previousGameMode);
            previousGameMode = null;
        }
    }
}
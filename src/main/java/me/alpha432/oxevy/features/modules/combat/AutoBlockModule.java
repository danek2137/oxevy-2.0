package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.world.item.ShieldItem;

public class AutoBlockModule extends Module {
    private final Setting<Boolean> autoSwap = bool("AutoSwap", true);
    private final Setting<Integer> blockTicks = num("BlockTicks", 6, 1, 20);

    private static AutoBlockModule INSTANCE;

    private int blockTimer = 0;
    private boolean blocking = false;

    public AutoBlockModule() {
        super("AutoBlock", "Holds shield to block incoming damage", Category.COMBAT);
        INSTANCE = this;
    }

    public static boolean isBlocking() {
        return INSTANCE != null && INSTANCE.blocking;
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        if (mc.player.getOffhandItem().getItem() instanceof ShieldItem || autoSwap.getValue()) {
            blocking = true;
            mc.options.keyUse.setDown(true);
            blockTimer = blockTicks.getValue();
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || !mc.player.isAlive() || mc.screen != null) return;

        if (blocking) {
            mc.options.keyUse.setDown(true);
            blockTimer--;
            if (blockTimer <= 0) {
                mc.options.keyUse.setDown(false);
                blocking = false;
            }
        }
    }

    @Override
    public void onDisable() {
        mc.options.keyUse.setDown(false);
        blocking = false;
        blockTimer = 0;
    }
}

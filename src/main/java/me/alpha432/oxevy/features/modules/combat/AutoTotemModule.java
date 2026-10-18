package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class AutoTotemModule extends Module {
    public final Setting<Boolean> checkHealth = bool("CheckHealth", false);
    public final Setting<Double> healthThreshold = num("HealthThreshold", 10.0, 1.0, 20.0);
    public final Setting<Boolean> openInventory = bool("OpenInventory", true);

    private int clickDelay = 0;
    private int foundSlot = -1;
    private int closeTicks = -1;

    public AutoTotemModule() {
        super("AutoTotem", "Auto holds totems", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        clickDelay = 0;
        foundSlot = -1;
        closeTicks = -1;
    }

    @Override
    public void onDisable() {
        if (closeTicks >= 0 && mc.player != null) {
            mc.setScreen(null);
            closeTicks = -1;
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || !mc.player.isAlive()) return;

        if (closeTicks > 0) {
            closeTicks--;
            return;
        }
        if (closeTicks == 0) {
            mc.setScreen(null);
            closeTicks = -1;
        }

        ItemStack offhand = mc.player.getOffhandItem();

        if (offhand.getItem() == Items.TOTEM_OF_UNDYING) {
            clickDelay = 0;
            foundSlot = -1;
            return;
        }

        boolean needTotem = !checkHealth.getValue() || mc.player.getHealth() <= healthThreshold.getValue();
        if (!needTotem) return;

        if (foundSlot < 0) {
            for (int i = 0; i < 36; i++) {
                if (mc.player.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    foundSlot = i;
                    break;
                }
            }
            if (foundSlot < 0) return;
        }

        int containerId = mc.player.containerMenu.containerId;

        if (clickDelay == 0) {
            int containerSlot = foundSlot < 9 ? foundSlot + 36 : foundSlot;
            mc.gameMode.handleInventoryMouseClick(containerId, containerSlot, 0, ClickType.PICKUP, mc.player);
            clickDelay = 1;

        } else if (clickDelay == 1) {
            mc.gameMode.handleInventoryMouseClick(containerId, 45, 0, ClickType.PICKUP, mc.player);
            clickDelay = 0;
            foundSlot = -1;

            if (openInventory.getValue()) {
                mc.setScreen(new InventoryScreen(mc.player));
                closeTicks = 2;
            }
        }
    }
}

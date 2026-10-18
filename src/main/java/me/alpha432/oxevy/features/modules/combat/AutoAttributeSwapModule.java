package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.world.item.ItemStack;

public class AutoAttributeSwapModule extends Module {
    private final Setting<Integer> swapDelay = num("SwapDelay", 1, 0, 4);

    private int currentSlot = -1;
    private int emptySlot = -1;
    private int swapState = 0;
    private int delay = 0;
    private boolean wasReady = true;

    public AutoAttributeSwapModule() {
        super("AutoAttributeSwap", "Swaps items to reset cooldown for no-cooldown lunge/spear/mace", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        swapState = 0;
        delay = 0;
        currentSlot = -1;
        emptySlot = -1;
        wasReady = true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.isAlive() || mc.screen != null) return;

        if (swapState > 0) {
            performSwap();
            return;
        }

        float cooldown = mc.player.getAttackStrengthScale(0.0f);
        boolean ready = cooldown >= 1.0f;

        if (wasReady && !ready) {
            startSwap();
        }

        wasReady = ready;
    }

    private void startSwap() {
        currentSlot = mc.player.getInventory().getSelectedSlot();

        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty() || !isWeapon(held)) return;

        emptySlot = findSwapSlot();
        if (emptySlot < 0) return;

        delay = swapDelay.getValue();
        swapState = 1;
    }

    private void performSwap() {
        if (delay > 0) {
            delay--;
            return;
        }

        switch (swapState) {
            case 1 -> {
                mc.player.getInventory().setSelectedSlot(emptySlot);
                mc.gameMode.ensureHasSentCarriedItem();
                delay = swapDelay.getValue();
                swapState = 2;
            }
            case 2 -> {
                mc.player.getInventory().setSelectedSlot(currentSlot);
                mc.gameMode.ensureHasSentCarriedItem();
                swapState = 0;
                currentSlot = -1;
                emptySlot = -1;
            }
        }
    }

    private int findSwapSlot() {
        for (int i = 0; i < 9; i++) {
            if (i == currentSlot) continue;
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i == currentSlot) continue;
            if (!isWeapon(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private boolean isWeapon(ItemStack stack) {
        return !stack.isStackable() && stack.getMaxDamage() > 0;
    }
}

package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class AutoArmorModule extends Module {
    public final Setting<Boolean> onlyWhenHurt = bool("OnlyWhenHurt", false);
    public final Setting<Boolean> openInventory = bool("OpenInventory", true);

    private int currentSlot = 0;
    private int clickDelay = 0;
    private int bestSlot = -1;
    private boolean swapping = false;
    private boolean didSwap = false;
    private int closeTicks = -1;

    private static final int[] ARMOR_CONTAINER_SLOTS = {5, 6, 7, 8};
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    public AutoArmorModule() {
        super("AutoArmor", "Auto equips the best armor", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        currentSlot = 0;
        clickDelay = 0;
        bestSlot = -1;
        swapping = false;
        didSwap = false;
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
        if (mc.player.containerMenu != mc.player.inventoryMenu) return;

        if (closeTicks > 0) {
            closeTicks--;
            return;
        }
        if (closeTicks == 0) {
            mc.setScreen(null);
            closeTicks = -1;
        }

        if (onlyWhenHurt.getValue() && mc.player.getHealth() >= mc.player.getMaxHealth()) {
            currentSlot = 0;
            return;
        }

        if (swapping) {
            handleSwap();
            return;
        }

        if (currentSlot >= 4) {
            if (didSwap && openInventory.getValue() && closeTicks < 0) {
                mc.setScreen(new InventoryScreen(mc.player));
                closeTicks = 2;
            }
            didSwap = false;
            currentSlot = 0;
            return;
        }

        bestSlot = findBestArmorIndex(currentSlot);
        if (bestSlot < 0) {
            currentSlot++;
            return;
        }

        swapping = true;
        didSwap = true;
        clickDelay = 0;
    }

    private void handleSwap() {
        int armorContainerSlot = ARMOR_CONTAINER_SLOTS[currentSlot];
        int containerId = mc.player.containerMenu.containerId;

        if (clickDelay == 0) {
            int invSlot = bestSlot < 9 ? bestSlot + 36 : bestSlot;
            mc.gameMode.handleInventoryMouseClick(containerId, invSlot, 0, ClickType.PICKUP, mc.player);
            clickDelay = 1;
        } else if (clickDelay == 1) {
            mc.gameMode.handleInventoryMouseClick(containerId, armorContainerSlot, 0, ClickType.PICKUP, mc.player);
            clickDelay = 2;
        } else if (clickDelay == 2) {
            if (!mc.player.containerMenu.getCarried().isEmpty()) {
                int dumpSlot = findEmptySlot();
                if (dumpSlot >= 0) {
                    int invSlot = dumpSlot < 9 ? dumpSlot + 36 : dumpSlot;
                    mc.gameMode.handleInventoryMouseClick(containerId, invSlot, 0, ClickType.PICKUP, mc.player);
                } else {
                    mc.gameMode.handleInventoryMouseClick(containerId, bestSlot < 9 ? bestSlot + 36 : bestSlot, 0, ClickType.PICKUP, mc.player);
                }
            }
            swapping = false;
            clickDelay = 0;
            bestSlot = -1;
            currentSlot++;
        }
    }

    private int findBestArmorIndex(int armorType) {
        int bestIndex = -1;
        double bestProtection = -1;
        double bestToughness = -1;

        ItemStack currentArmor = mc.player.getInventory().getItem(getArmorInventoryIndex(armorType));
        if (!currentArmor.isEmpty()) {
            bestProtection = getArmorValue(currentArmor, EQUIPMENT_SLOTS[armorType]);
            bestToughness = getToughnessValue(currentArmor, EQUIPMENT_SLOTS[armorType]);
        }

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (!isArmorType(stack, armorType)) continue;

            double prot = getArmorValue(stack, EQUIPMENT_SLOTS[armorType]);
            double tough = getToughnessValue(stack, EQUIPMENT_SLOTS[armorType]);

            if (prot > bestProtection || (prot == bestProtection && tough > bestToughness)) {
                bestProtection = prot;
                bestToughness = tough;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private boolean isArmorType(ItemStack stack, int armorType) {
        return switch (armorType) {
            case 0 -> stack.is(ItemTags.HEAD_ARMOR);
            case 1 -> stack.is(ItemTags.CHEST_ARMOR);
            case 2 -> stack.is(ItemTags.LEG_ARMOR);
            case 3 -> stack.is(ItemTags.FOOT_ARMOR);
            default -> false;
        };
    }

    private double getArmorValue(ItemStack stack, EquipmentSlot slot) {
        final double[] value = {-1};
        stack.forEachModifier(slot, (attr, mod) -> {
            if (attr.is(Attributes.ARMOR) && mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                value[0] = mod.amount();
            }
        });
        return value[0];
    }

    private double getToughnessValue(ItemStack stack, EquipmentSlot slot) {
        final double[] value = {-1};
        stack.forEachModifier(slot, (attr, mod) -> {
            if (attr.is(Attributes.ARMOR_TOUGHNESS) && mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                value[0] = mod.amount();
            }
        });
        return value[0];
    }

    private int findEmptySlot() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private int getArmorInventoryIndex(int armorType) {
        return switch (armorType) {
            case 0 -> 39;
            case 1 -> 38;
            case 2 -> 37;
            case 3 -> 36;
            default -> -1;
        };
    }
}

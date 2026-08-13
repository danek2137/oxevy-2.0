package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class InventoryHudModule extends HudModule {
    public final Setting<Boolean> showHotbar = bool("ShowHotbar", true);
    public final Setting<Boolean> showArmor = bool("ShowArmor", true);
    public final Setting<Boolean> showOffhand = bool("ShowOffhand", true);

    private static final int SLOT_SIZE = 16;
    private static final int PADDING = 2;
    private static final int COLS = 9;

    public InventoryHudModule() {
        super("Inventory", "Shows your inventory on the HUD", 180, 100);
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (nullCheck()) return;

        float x = getX();
        float y = getY();
        GuiGraphics ctx = e.getContext();

        Inventory inv = mc.player.getInventory();
        int rows = (showHotbar.getValue() ? 1 : 0) + 3;

        int gridWidth = COLS * (SLOT_SIZE + PADDING) - PADDING;
        int startX = (int) x;
        int startY = (int) y;

        if (showArmor.getValue()) {
            int armorY = startY;
            for (int i = 3; i >= 0; i--) {
                EquipmentSlot slot = switch (i) {
                    case 3 -> EquipmentSlot.HEAD;
                    case 2 -> EquipmentSlot.CHEST;
                    case 1 -> EquipmentSlot.LEGS;
                    default -> EquipmentSlot.FEET;
                };
                ItemStack stack = mc.player.getItemBySlot(slot);
                int sx = startX + gridWidth + PADDING;
                ctx.renderItem(stack, sx, armorY);
                ctx.renderItemDecorations(mc.font, stack, sx, armorY);
                armorY += SLOT_SIZE + PADDING;
            }
        }

        if (showOffhand.getValue()) {
            ItemStack offhand = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
            int sx = startX + gridWidth + PADDING;
            int sy = startY + (showArmor.getValue() ? 4 * (SLOT_SIZE + PADDING) + PADDING : 0);
            ctx.renderItem(offhand, sx, sy);
            ctx.renderItemDecorations(mc.font, offhand, sx, sy);
        }

        int slotIdx = 0;
        if (showHotbar.getValue()) {
            for (int col = 0; col < 9; col++) {
                ItemStack stack = inv.getItem(col);
                int sx = startX + col * (SLOT_SIZE + PADDING);
                int sy = startY;
                ctx.renderItem(stack, sx, sy);
                ctx.renderItemDecorations(mc.font, stack, sx, sy);
                if (col == inv.selected) {
                    ctx.fill(sx - 1, sy - 1, sx + SLOT_SIZE + 1, sy + SLOT_SIZE + 1, 0x55FFFFFF);
                }
            }
            slotIdx = 9;
        }

        int mainY = startY + (showHotbar.getValue() ? SLOT_SIZE + PADDING : 0);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                ItemStack stack = inv.getItem(slotIdx);
                int sx = startX + col * (SLOT_SIZE + PADDING);
                int sy = mainY + row * (SLOT_SIZE + PADDING);
                ctx.renderItem(stack, sx, sy);
                ctx.renderItemDecorations(mc.font, stack, sx, sy);
                slotIdx++;
            }
        }

        int totalW = gridWidth + PADDING;
        if (showArmor.getValue() || showOffhand.getValue()) {
            totalW += SLOT_SIZE + PADDING;
        }
        int totalH = rows * (SLOT_SIZE + PADDING) - PADDING;

        setWidth(Math.max(80, totalW));
        setHeight(Math.max(30, totalH));
    }
}

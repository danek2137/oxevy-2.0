package me.alpha432.oxevy.mixin.gui;

import me.alpha432.oxevy.features.modules.misc.ShulkerPreviewModule;
import me.alpha432.oxevy.manager.ModuleManager;
import me.alpha432.oxevy.Oxevy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MixinTooltipRenderer {

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_PAD = 1;
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int HEADER_H = 12;
    private static final int PADDING = 5;

    @Unique
    private boolean oxevy$previewPinned;

    @Unique
    private int oxevy$pinnedX;

    @Unique
    private int oxevy$pinnedY;

    @Unique
    private NonNullList<ItemStack> oxevy$pinnedItems;

    @Inject(method = "renderWithTooltipAndSubtitles", at = @At("RETURN"))
    private void renderShulkerPreview(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!(self instanceof AbstractContainerScreen<?> screen)) return;

        ModuleManager mm = Oxevy.moduleManager;
        if (mm == null) return;
        ShulkerPreviewModule module = mm.getModuleByClass(ShulkerPreviewModule.class);
        if (module == null || !module.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().handle();
        boolean altHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

        NonNullList<ItemStack> itemStacks = null;

        if (altHeld && oxevy$previewPinned && oxevy$pinnedItems != null) {
            itemStacks = oxevy$pinnedItems;
        } else {
            Slot hoveredSlot = ((MixinAbstractContainerScreenAccessor) screen).getHoveredSlot();
            if (hoveredSlot == null) {
                oxevy$previewPinned = false;
                oxevy$pinnedItems = null;
                return;
            }

            ItemStack stack = hoveredSlot.getItem();
            if (stack.isEmpty() || !ShulkerPreviewModule.isShulkerBox(stack)) {
                oxevy$previewPinned = false;
                oxevy$pinnedItems = null;
                return;
            }

            var contents = ShulkerPreviewModule.getShulkerContents(stack);
            if (contents == null || contents == net.minecraft.world.item.component.ItemContainerContents.EMPTY) {
                oxevy$previewPinned = false;
                oxevy$pinnedItems = null;
                return;
            }

            NonNullList<ItemStack> slotItems = NonNullList.withSize(27, ItemStack.EMPTY);
            ShulkerPreviewModule.copyContentsInto(contents, slotItems);

            boolean hasItems = false;
            for (ItemStack s : slotItems) {
                if (!s.isEmpty()) { hasItems = true; break; }
            }
            if (!hasItems) {
                oxevy$previewPinned = false;
                oxevy$pinnedItems = null;
                return;
            }

            if (altHeld) {
                oxevy$previewPinned = true;
                oxevy$pinnedItems = slotItems;
            }
            itemStacks = slotItems;
        }

        int contentW = COLS * (SLOT_SIZE + SLOT_PAD) - SLOT_PAD;
        int contentH = ROWS * (SLOT_SIZE + SLOT_PAD) - SLOT_PAD;
        int totalW = contentW + PADDING * 2;
        int totalH = contentH + HEADER_H + PADDING * 2;

        int px, py;
        if (oxevy$previewPinned) {
            px = oxevy$pinnedX;
            py = oxevy$pinnedY;
        } else {
            px = mouseX + 12;
            py = mouseY;
            if (px + totalW > self.width) px = mouseX - totalW - 2;
            if (py + totalH > self.height) py = self.height - totalH;
            if (py < 0) py = 0;
        }

        if (altHeld && !oxevy$previewPinned) {
            oxevy$pinnedX = px;
            oxevy$pinnedY = py;
        }

        int bgColor = 0xF0100010;
        int borderColor = 0xFF388D38;
        int headerColor = 0xFF2E7D32;

        context.fill(px - 1, py - 1, px + totalW + 1, py + totalH + 1, borderColor);
        context.fill(px, py, px + totalW, py + totalH, bgColor);
        context.fill(px, py, px + totalW, py + HEADER_H, headerColor);

        context.drawString(mc.font, "Shulker Box", px + PADDING, py + 2, 0xFFFFFFFF, true);

        int gridX = px + PADDING;
        int gridY = py + HEADER_H + PADDING;

        int hoveredIdx = -1;
        if (oxevy$previewPinned) {
            int relX = mouseX - gridX;
            int relY = mouseY - gridY;
            if (relX >= 0 && relY >= 0) {
                int col = relX / (SLOT_SIZE + SLOT_PAD);
                int row = relY / (SLOT_SIZE + SLOT_PAD);
                if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
                    int cellX = col * (SLOT_SIZE + SLOT_PAD);
                    int cellY = row * (SLOT_SIZE + SLOT_PAD);
                    if (relX - cellX < SLOT_SIZE && relY - cellY < SLOT_SIZE) {
                        hoveredIdx = row * COLS + col;
                    }
                }
            }
        }

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int sx = gridX + col * (SLOT_SIZE + SLOT_PAD);
                int sy = gridY + row * (SLOT_SIZE + SLOT_PAD);
                int idx = row * COLS + col;
                int slotBg = (idx == hoveredIdx) ? 0xFF3A3A3A : 0xFF282828;
                context.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, slotBg);
            }
        }

        for (int i = 0; i < 27; i++) {
            ItemStack itemStack = itemStacks.get(i);
            if (itemStack.isEmpty()) continue;
            int col = i % COLS;
            int row = i / COLS;
            int sx = gridX + col * (SLOT_SIZE + SLOT_PAD);
            int sy = gridY + row * (SLOT_SIZE + SLOT_PAD);
            context.renderItem(itemStack, sx, sy);
            context.renderItemDecorations(mc.font, itemStack, sx, sy);
        }

        if (hoveredIdx >= 0 && hoveredIdx < 27) {
            ItemStack hoveredItem = itemStacks.get(hoveredIdx);
            if (!hoveredItem.isEmpty()) {
                context.setTooltipForNextFrame(mc.font, hoveredItem, mouseX, mouseY);
            }
        }
    }
}

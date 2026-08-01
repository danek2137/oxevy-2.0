package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.AnimationUtil;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class KeystrokesHudModule extends HudModule {
    public final Setting<Boolean> showMouse = bool("Mouse", true);
    public final Setting<Boolean> showSpace = bool("Space", true);
    public final Setting<Float> keySize = num("KeySize", 20f, 12f, 30f);
    public final Setting<Integer> padding = num("Padding", 2, 0, 8);
    public final Setting<Float> animationSpeed = num("AnimationSpeed", 0.15f, 0.01f, 0.5f);

    private final Map<Integer, Float> keyAnimations = new HashMap<>();

    public KeystrokesHudModule() {
        super("Keystrokes", "Shows pressed movement keys", 60, 60);
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (nullCheck()) return;

        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();

        float size = keySize.getValue();
        int pad = padding.getValue();

        float totalW = size * 3 + pad * 2;
        float totalH = size * 2 + pad;
        if (showMouse.getValue()) totalH += size + pad;
        if (showSpace.getValue()) totalH += size / 2f + pad;

        setWidth(totalW);
        setHeight(totalH);

        // Row 1: W centered
        drawKey(ctx, x + size + pad, y, size, size, "W", GLFW.GLFW_KEY_W, false);

        // Row 2: A S D
        float row2Y = y + size + pad;
        drawKey(ctx, x, row2Y, size, size, "A", GLFW.GLFW_KEY_A, false);
        drawKey(ctx, x + size + pad, row2Y, size, size, "S", GLFW.GLFW_KEY_S, false);
        drawKey(ctx, x + (size + pad) * 2, row2Y, size, size, "D", GLFW.GLFW_KEY_D, false);

        float nextY = row2Y + size + pad;

        if (showMouse.getValue()) {
            float mouseW = (totalW - pad) / 2f;
            drawKey(ctx, x, nextY, mouseW, size, "LMB", GLFW.GLFW_MOUSE_BUTTON_1, true);
            drawKey(ctx, x + mouseW + pad, nextY, mouseW, size, "RMB", GLFW.GLFW_MOUSE_BUTTON_2, true);
            nextY += size + pad;
        }

        if (showSpace.getValue()) {
            drawKey(ctx, x, nextY, totalW, size / 2f, "____", GLFW.GLFW_KEY_SPACE, false);
        }
    }

    private void drawKey(GuiGraphics ctx, float x, float y, float w, float h, String label, int key, boolean isMouse) {
        boolean pressed = isPressed(key, isMouse);
        float target = pressed ? 1.0f : 0.0f;
        int animKey = isMouse ? key - 100 : key; // Offset mouse keys to avoid collision
        keyAnimations.putIfAbsent(animKey, 0.0f);
        float anim = AnimationUtil.animate(keyAnimations.get(animKey), target, animationSpeed.getValue());
        keyAnimations.put(animKey, anim);

        int pressColor = ClickGuiModule.getInstance().color.getValue().getRGB();
        int color = AnimationUtil.interpolateColor(0xFF11111B, pressColor, anim);

        if (rounded.getValue()) {
            RenderUtil.roundRect(ctx, x, y, w, h, radius.getValue(), color);
        } else {
            RenderUtil.rect(ctx, x, y, x + w, y + h, color);
        }

        int textColor = AnimationUtil.interpolateColor(0xFFAAAAAA, 0xFFFFFFFF, anim);
        int tw = mc.font.width(label);
        int th = mc.font.lineHeight;
        ctx.drawString(mc.font, label, (int) (x + (w - tw) / 2f), (int) (y + (h - th) / 2f), textColor);
    }

    private boolean isPressed(int key, boolean isMouse) {
        if (isMouse) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
    }
}

package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.features.settings.Bind;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.KeyboardUtil;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;

import java.awt.Color;

import org.lwjgl.glfw.GLFW;

public class BindComponent extends SettingComponent {
    private static final Minecraft mc = Minecraft.getInstance();
    private boolean listening = false;
    public BindComponent(Setting<Bind> setting, int x, int y, int width, int height) {
        super(setting, x, y, width, height);
    }
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Color accent = ClickGuiModule.getInstance().color.getValue();
        boolean hovered = isHovered(mouseX, mouseY);
        RenderUtil.rect(context, x, y, x + width, y + height, hovered ? 0xFF1E1E30 : 0xFF1A1A28);
        int textY = y + (height - 8) / 2;
        context.drawString(CustomFont.font(), setting.getName(), x + 6, textY, 0xFFE6E6E6);
        String val = listening ? "..." : KeyboardUtil.getKeyName(((Bind)setting.getValue()).getKey());
        int color = listening
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 230).getRGB()
                : 0xFFD4D4D8;
        context.drawString(CustomFont.font(), listening ? "Press key..." : val, x + width - 6 - CustomFont.font().width(listening ? "Press key..." : val), textY, color);
    }
    @Override public boolean mouseClicked(double mx, double my, int b) {
        if (isHovered(mx, my)) {
            if (b == 0) {
                listening = true;
            } else if (b == 1) {
                setting.setValue(Bind.none());
                listening = false;
            }
            return true;
        }
        return false;
    }
    @Override public boolean keyPressed(KeyEvent input) {
        if (!listening) {
            return false;
        }

        int key = input.key();
        Bind bind = switch (key) {
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_ESCAPE -> Bind.none();
            default -> new Bind(key);
        };

        setting.setValue(bind);
        listening = false;
        return true;
    }
    public boolean isListening() { return listening; }
    public void stopListening() { listening = false; }
    @Override public boolean isCapturingInput() { return listening; }
}

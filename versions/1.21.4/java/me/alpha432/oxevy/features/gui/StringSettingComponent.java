package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.util.KeyboardUtil;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

import org.lwjgl.glfw.GLFW;

public class StringSettingComponent extends SettingComponent {
    private static final Minecraft mc = Minecraft.getInstance();
    private boolean listening = false;
    private String buffer;
    
    public StringSettingComponent(Setting<?> setting, int x, int y, int width, int height) {
        super(setting, x, y, width, height);
        this.buffer = setting.getValueAsString();
    }
    
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Color accent = ClickGuiModule.getInstance().color.getValue();
        RenderUtil.roundRect(context, x, y, width, height, 10f, isHovered(mouseX, mouseY) ? 0x1AFFFFFF : 0x12FFFFFF);
        context.drawString(CustomFont.font(), setting.getName(), x + 10, y + 8, 0xFFE6E6E6);

        String baseText = listening ? buffer : setting.getValueAsString();
        String cursor = listening && (System.currentTimeMillis() / 400L) % 2L == 0L ? "_" : "";
        String valueText = baseText + cursor;
        int textColor = listening
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 235).getRGB()
                : 0xFFD4D4D8;
        context.drawString(CustomFont.font(), valueText, x + width - 12 - CustomFont.font().width(valueText), y + 8, textColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isHovered(mouseX, mouseY)) {
            return false;
        }

        if (button == 0) {
            listening = true;
            buffer = String.valueOf(setting.getValue());
        } else if (button == 1) {
            setting.reset();
            buffer = String.valueOf(setting.getValue());
            listening = false;
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!listening) {
            return false;
        }

        int key = keyCode;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            listening = false;
            buffer = String.valueOf(setting.getValue());
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!buffer.isEmpty()) {
                buffer = buffer.substring(0, buffer.length() - 1);
            }
            return true;
        }

        String typed = KeyboardUtil.getTypedCharacter(key, KeyboardUtil.isShiftDown());
        if (typed != null && !typed.isEmpty()) {
            buffer += typed;
        }
        return true;
    }

    private void commit() {
        setting.setValue(buffer.isEmpty() ? setting.getDefaultValue() : buffer);
        buffer = String.valueOf(setting.getValue());
        listening = false;
    }

    @Override
    public boolean isCapturingInput() {
        return listening;
    }
}

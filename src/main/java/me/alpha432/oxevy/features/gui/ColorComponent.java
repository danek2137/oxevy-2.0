package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.*;

public class ColorComponent extends SettingComponent {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final String[] CHANNEL_NAMES = {"Red", "Green", "Blue", "Alpha"};
    private static final Color[] CHANNEL_COLORS = {
            new Color(255, 90, 90),
            new Color(90, 255, 150),
            new Color(90, 170, 255),
            new Color(225, 225, 225)
    };

    private boolean open = false;
    private int draggingChannel = -1;
    public ColorComponent(Setting<Color> setting, int x, int y, int width, int height) {
        super(setting, x, y, width, height);
    }
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Color c = (Color) setting.getValue();
        Color accent = ClickGuiModule.getInstance().color.getValue();
        boolean hovered = isHovered(mouseX, mouseY);
        int totalH = getHeight();
        RenderUtil.rect(context, x, y, x + width, y + totalH, hovered ? 0xFF1E1E30 : 0xFF1A1A28);
        int textY = y + (height - 8) / 2;
        context.drawString(CustomFont.font(), setting.getName(), x + 6, textY, 0xFFE6E6E6);
        String preview = String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
        context.drawString(CustomFont.font(), preview, x + width - 50 - CustomFont.font().width(preview), textY, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 235).getRGB());
        RenderUtil.rect(context, x + width - 30, y + 3, x + width - 6, y + height - 3, c.getRGB());

        if (open) {
            int contentY = y + height + 8;
            for (int i = 0; i < CHANNEL_NAMES.length; i++) {
                int rowY = contentY + i * 22;
                int value = getChannelValue(c, i);
                drawChannelRow(context, CHANNEL_NAMES[i], CHANNEL_COLORS[i], value, x + 6, rowY, width - 12);
            }

            if (draggingChannel >= 0) {
                updateChannel(mouseX, draggingChannel);
            }
        }
    }

    private void drawChannelRow(GuiGraphics context, String label, Color color, int value, int rowX, int rowY, int rowWidth) {
        context.drawString(CustomFont.font(), label, rowX, rowY, 0xFFBDBDC7);
        String valueText = String.valueOf(value);
        context.drawString(CustomFont.font(), valueText, rowX + rowWidth - CustomFont.font().width(valueText), rowY, 0xFFD4D4D8);

        int trackY = rowY + 12;
        int trackWidth = rowWidth;
        int knobX = rowX + Math.round((value / 255f) * trackWidth);

        RenderUtil.roundRect(context, rowX, trackY, trackWidth, 4, 2f, 0xFF2F2F38);
        RenderUtil.roundRect(context, rowX, trackY, Math.max(4, knobX - rowX), 4, 2f, color.getRGB());
        RenderUtil.roundRect(context, knobX - 4, trackY - 4, 8, 12, 4f, 0xFFFFFFFF);
    }

    private int getChannelValue(Color color, int channel) {
        return switch (channel) {
            case 0 -> color.getRed();
            case 1 -> color.getGreen();
            case 2 -> color.getBlue();
            case 3 -> color.getAlpha();
            default -> 0;
        };
    }

    private void updateChannel(double mouseX, int channel) {
        int rowX = x + 6;
        int rowWidth = width - 12;
        float percent = (float) Math.max(0.0, Math.min(1.0, (mouseX - rowX) / rowWidth));
        int nextValue = Math.round(percent * 255f);
        Color current = (Color) setting.getValue();
        Color updated = switch (channel) {
            case 0 -> new Color(nextValue, current.getGreen(), current.getBlue(), current.getAlpha());
            case 1 -> new Color(current.getRed(), nextValue, current.getBlue(), current.getAlpha());
            case 2 -> new Color(current.getRed(), current.getGreen(), nextValue, current.getAlpha());
            case 3 -> new Color(current.getRed(), current.getGreen(), current.getBlue(), nextValue);
            default -> current;
        };
        setting.setValue(updated);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0 && open) {
            int contentY = y + height + 8;
            for (int i = 0; i < CHANNEL_NAMES.length; i++) {
                int rowY = contentY + i * 22;
                if (mx >= x + 10 && mx <= x + width - 10 && my >= rowY && my <= rowY + 20) {
                    draggingChannel = i;
                    updateChannel(mx, i);
                    return true;
                }
            }
        }

        if (isHovered(mx, my)) {
            if (b == 0) {
                open = !open;
            } else if (b == 1) {
                setting.reset();
                open = false;
            }
            return true;
        }

        return false;
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        draggingChannel = -1;
    }

    @Override
    public int getHeight() { return open ? height + 8 + CHANNEL_NAMES.length * 22 : height; }

    @Override
    public boolean isCapturingInput() {
        return draggingChannel >= 0;
    }
}

package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.Locale;

public class SliderComponent extends SettingComponent {
    private static final Minecraft mc = Minecraft.getInstance();
    private boolean dragging = false;
    
    public SliderComponent(Setting<Number> setting, int x, int y, int width, int height) {
        super(setting, x, y, width, height);
    }
    
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        float min = ((Number) setting.getMin()).floatValue();
        float max = ((Number) setting.getMax()).floatValue();
        float value = ((Number) setting.getValue()).floatValue();
        float percent = (value - min) / (max - min);

        Color accent = ClickGuiModule.getInstance().color.getValue();
        boolean hovered = isHovered(mouseX, mouseY);
        RenderUtil.rect(context, x, y, x + width, y + height, hovered ? 0xFF1E1E30 : 0xFF1A1A28);

        int textY = y + 1;
        context.drawString(CustomFont.font(), setting.getName(), x + 6, textY, 0xFFE6E6E6);

        String valueText = formatValue((Number) setting.getValue());
        context.drawString(CustomFont.font(), valueText, x + width - 6 - CustomFont.font().width(valueText), textY, accent.getRGB());

        int trackX = x + 6;
        int trackY = y + height - 4;
        int trackWidth = width - 12;
        int activeTrack = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 200).getRGB();
        int knobX = trackX + Math.round(percent * trackWidth);

        RenderUtil.rect(context, trackX, trackY, trackX + trackWidth, trackY + 2, 0xFF2F2F38);
        RenderUtil.rect(context, trackX, trackY, Math.max(trackX, knobX), trackY + 2, activeTrack);

        RenderUtil.rect(context, knobX - 2, trackY - 1, knobX + 2, trackY + 3, 0xFFFFFFFF);

        if (dragging) updateValue(mouseX);
    }
    
    private void updateValue(double mouseX) {
        float min = ((Number) setting.getMin()).floatValue();
        float max = ((Number) setting.getMax()).floatValue();
        double percent = Math.max(0, Math.min(1, (mouseX - (x + 6)) / (width - 12)));
        float val = (float) (min + percent * (max - min));
        if (setting.getValue() instanceof Integer) setting.setValue(Math.round(val));
        else if (setting.getValue() instanceof Float) setting.setValue((float) (Math.round(val * 10.0) / 10.0));
        else if (setting.getValue() instanceof Double) setting.setValue(Math.round(val * 10.0) / 10.0);
    }
    
    private String formatValue(Number number) {
        if (number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte) {
            return String.valueOf(number.intValue());
        }
        return String.format(Locale.ROOT, "%.1f", number.doubleValue());
    }
    
    @Override public boolean mouseClicked(double mx, double my, int b) {
        if (isHovered(mx, my) && b == 0) {
            dragging = true;
            updateValue(mx);
            return true;
        }
        return false;
    }
    @Override public void mouseReleased(double mx, double my, int b) { dragging = false; }
    @Override public boolean isCapturingInput() { return dragging; }
}

package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

public class BooleanComponent extends SettingComponent {
    private static final Minecraft mc = Minecraft.getInstance();
    public BooleanComponent(Setting<Boolean> setting, int x, int y, int width, int height) {
        super(setting, x, y, width, height);
    }
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean val = (Boolean) setting.getValue();
        boolean hovered = isHovered(mouseX, mouseY);
        Color accent = ClickGuiModule.getInstance().color.getValue();

        RenderUtil.roundRect(context, x, y, width, height, 10f, hovered ? 0x1AFFFFFF : 0x12FFFFFF);
        context.drawString(CustomFont.font(), setting.getName(), x + 10, y + (height - 8) / 2, 0xFFE6E6E6);

        int trackWidth = 22;
        int trackHeight = 10;
        int trackX = x + width - trackWidth - 10;
        int trackY = y + (height - trackHeight) / 2;
        int activeTrack = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220).getRGB();

        RenderUtil.roundRect(context, trackX, trackY, trackWidth, trackHeight, 5f, val ? activeTrack : 0xFF2F2F38);
        RenderUtil.roundRect(context, val ? trackX + trackWidth - 9 : trackX + 1, trackY + 1, 8, 8, 4f, 0xFFFFFFFF);
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            setting.setValue(!(Boolean) setting.getValue());
            return true;
        }
        return false;
    }
}

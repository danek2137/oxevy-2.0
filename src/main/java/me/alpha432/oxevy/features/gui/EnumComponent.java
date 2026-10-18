package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

public class EnumComponent extends SettingComponent {
    private static final Minecraft mc = Minecraft.getInstance();
    public EnumComponent(Setting<?> setting, int x, int y, int width, int height) {
        super(setting, x, y, width, height);
    }
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Color accent = ClickGuiModule.getInstance().color.getValue();
        boolean hovered = isHovered(mouseX, mouseY);
        RenderUtil.rect(context, x, y, x + width, y + height, hovered ? 0xFF1E1E30 : 0xFF1A1A28);
        int textY = y + (height - 8) / 2;
        context.drawString(CustomFont.font(), setting.getName(), x + 6, textY, 0xFFE6E6E6);
        String val = setting.currentEnumName();
        context.drawString(CustomFont.font(), val, x + width - 18 - CustomFont.font().width(val), textY, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 230).getRGB());
        context.drawString(CustomFont.font(), ">", x + width - 10, textY, 0xFF9E9EAA);
    }
    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (isHovered(mx, my)) {
            if (b == 0) setting.increaseEnum();
            else if (b == 1) setting.reset();
            return true;
        }
        return false;
    }
}

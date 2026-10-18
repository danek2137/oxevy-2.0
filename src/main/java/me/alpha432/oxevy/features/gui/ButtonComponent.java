package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

public class ButtonComponent extends SettingComponent {
    private static final Minecraft mc = Minecraft.getInstance();
    
    public ButtonComponent(Setting<Runnable> setting, int x, int y, int width, int height) {
        super(setting, x, y, width, height);
    }
    
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered(mouseX, mouseY);
        Color accent = ClickGuiModule.getInstance().color.getValue();

        int bgColor = hovered ? 0xFF1E1E30 : 0xFF1A1A28;
        RenderUtil.rect(context, x, y, x + width, y + height, bgColor);

        // Left accent bar (similar to enabled-module indicator)
        if (hovered) {
            int accentColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 200).getRGB();
            RenderUtil.rect(context, x, y, x + 3, y + height, accentColor);
        }
        
        // Label - accent-colored when hovered so it looks clickable
        String label = setting.getName();
        int textColor = hovered
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 255).getRGB()
                : 0xFFE6E6E6;
        context.drawString(CustomFont.font(), label, x + 6, y + (height - 8) / 2, textColor);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            setting.run();
            return true;
        }
        return false;
    }
}

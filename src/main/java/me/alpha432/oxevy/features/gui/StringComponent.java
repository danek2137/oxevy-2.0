package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class StringComponent extends SettingComponent {
    private static final Minecraft mc = Minecraft.getInstance();
    private boolean focused = false;
    
    public StringComponent(Setting<String> setting, int x, int y, int width, int height) {
        super(setting, x, y, width, height);
    }
    
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered(mouseX, mouseY);
        
        // Background - consistent with other components & modules in clickgui
        RenderUtil.rect(context, x, y, x + width, y + height, hovered ? 0xFF1E1E30 : 0xFF1A1A28);
        
        context.drawString(CustomFont.font(), setting.getName(), x + 6, y + 2, 0xFFE6E6E6);
        
        // Input box background
        int boxY = y + height - 12;
        RenderUtil.rect(context, x + 6, boxY, x + width - 6, boxY + 10, focused ? 0xFF2A2A3C : 0xFF0E0E18);
        
        // Value
        String value = (String) setting.getValue();
        String displayText = value + (focused ? (System.currentTimeMillis() % 1000 > 500 ? "_" : "") : "");
        if (value.isEmpty() && !focused) displayText = "Type...";
        
        context.drawString(CustomFont.font(), displayText, x + 10, boxY + 1, focused ? 0xFFFFFFFF : 0xFFAAAAAA);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            focused = true;
            return true;
        } else {
            focused = false;
        }
        return false;
    }
    
    public boolean isFocused() {
        return focused;
    }
    
    public void setFocused(boolean focused) {
        this.focused = focused;
    }
}

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
        
        // Background
        RenderUtil.rect(context, x, y, x + width, y + height, 0x11FFFFFF);
        
        context.drawString(CustomFont.font(), setting.getName(), x + 5, y + 2, 0xFFBBBBBB);
        
        // Input box background
        int boxY = y + height - 12;
        RenderUtil.rect(context, x + 5, boxY, x + width - 5, boxY + 10, focused ? 0x22FFFFFF : 0x11FFFFFF);
        
        // Value
        String value = (String) setting.getValue();
        String displayText = value + (focused ? (System.currentTimeMillis() % 1000 > 500 ? "_" : "") : "");
        if (value.isEmpty() && !focused) displayText = "Type...";
        
        context.drawString(CustomFont.font(), displayText, x + 10, boxY + 1, focused ? 0xFFFFFFFF : 0xFF888888);
        
        if (hovered) {
            RenderUtil.rect(context, x, y, x + width, y + height, 0x11FFFFFF);
        }
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

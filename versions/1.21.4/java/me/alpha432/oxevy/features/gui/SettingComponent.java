package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;

public abstract class SettingComponent {
    public int x, y;
    public final int width, height;
    protected final Setting setting;
    
    public SettingComponent(Setting setting, int x, int y, int width, int height) {
        this.setting = setting;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public abstract void render(GuiGraphics context, int mouseX, int mouseY, float delta);
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    public void mouseReleased(double mouseX, double mouseY, int button) {}
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    public boolean isCapturingInput() { return false; }
    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + getHeight();
    }
    public int getHeight() { return height; }
    public Setting getSetting() { return setting; }
}

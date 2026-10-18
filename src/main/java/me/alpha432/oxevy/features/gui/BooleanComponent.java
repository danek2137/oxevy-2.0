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
    private float animProgress = -1f;
    private long lastTime = 0;

    public BooleanComponent(Setting<Boolean> setting, int x, int y, int width, int height) {
        super(setting, x, y, width, height);
    }
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean val = (Boolean) setting.getValue();
        boolean hovered = isHovered(mouseX, mouseY);
        Color accent = ClickGuiModule.getInstance().color.getValue();

        long now = System.nanoTime();
        float dt = lastTime == 0 ? 0.016f : (float) ((now - lastTime) / 1e9);
        lastTime = now;
        dt = Math.min(dt, 0.1f);

        float target = val ? 1f : 0f;
        if (animProgress < 0f) animProgress = target;
        ClickGuiModule cgm = ClickGuiModule.getInstance();
        if (!cgm.animationsEnabled.getValue()) {
            animProgress = target;
        } else if (animProgress != target) {
            float speed = Math.max(cgm.animationSpeed.getValue(), 0.01f);
            float duration = Math.max(0.08f, Math.min(0.35f, 0.025f / speed));
            if (target > animProgress) {
                animProgress = Math.min(target, animProgress + dt / duration);
            } else {
                animProgress = Math.max(target, animProgress - dt / duration);
            }
        }
        float p = animProgress * animProgress * (3f - 2f * animProgress);

        RenderUtil.rect(context, x, y, x + width, y + height, hovered ? 0xFF1E1E30 : 0xFF1A1A28);
        context.drawString(CustomFont.font(), setting.getName(), x + 6, y + (height - 8) / 2, 0xFFE6E6E6);

        int trackWidth = 22;
        int trackHeight = 10;
        int trackX = x + width - trackWidth - 6;
        int trackY = y + (height - trackHeight) / 2;

        int offColor = 0xFF2F2F38;
        int onColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220).getRGB();
        int r = (int) ((((offColor >> 16) & 0xFF) * (1f - p)) + (((onColor >> 16) & 0xFF) * p));
        int g = (int) ((((offColor >> 8) & 0xFF) * (1f - p)) + (((onColor >> 8) & 0xFF) * p));
        int b = (int) (((offColor & 0xFF) * (1f - p)) + ((onColor & 0xFF) * p));
        int a = (int) ((((offColor >> 24) & 0xFF) * (1f - p)) + (((onColor >> 24) & 0xFF) * p));
        int trackBg = (a << 24) | (r << 16) | (g << 8) | b;

        float knobX = (trackX + 1) + (trackWidth - 10) * p;

        RenderUtil.roundRect(context, trackX, trackY, trackWidth, trackHeight, 5f, trackBg);
        RenderUtil.roundRect(context, knobX, trackY + 1, 8, 8, 4f, 0xFFFFFFFF);
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

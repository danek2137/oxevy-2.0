package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public class MemoryHudModule extends HudModule {
    public final Setting<Boolean> showBar = bool("ShowBar", true);
    public final Setting<Boolean> showMax = bool("ShowMax", false);

    private static final float BAR_WIDTH = 80;
    private static final float BAR_HEIGHT = 4;

    public MemoryHudModule() {
        super("Memory", "Shows RAM usage", 90, 30);
    }

    @Override
    public void drawContent(Render2DEvent e) {
        float x = getX();
        float y = getY();
        GuiGraphics ctx = e.getContext();
        int lineHeight = mc.font.lineHeight;

        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;
        double percent = usedMemory / (double) maxMemory;

        String text;
        int color;
        if (percent < 0.6) {
            color = 0xFF44FF44;
        } else if (percent < 0.8) {
            color = 0xFFFFFF44;
        } else {
            color = 0xFFFF4444;
        }

        if (showMax.getValue()) {
            text = "§fRAM: " + formatMB(usedMemory) + "/" + formatMB(maxMemory) + " MB";
        } else {
            text = "§fRAM: " + String.format(Locale.US, "%d%%", (int) (percent * 100));
        }

        int textWidth = mc.font.width(text);
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        boolean isLeft = x < screenWidth / 2.0f;
        int textX = isLeft ? (int) (x + 2) : (int) (x + getWidth() - 2 - textWidth);

        ctx.drawString(mc.font, text, textX, (int) y, color);

        int maxWidth = textWidth;

        if (showBar.getValue()) {
            float barX = x + 2;
            float barY = y + lineHeight + 2;

            RenderUtil.roundRect(ctx, barX, barY, BAR_WIDTH, BAR_HEIGHT, 2, 0xFF222222);
            RenderUtil.roundRect(ctx, barX, barY, (float) (BAR_WIDTH * percent), BAR_HEIGHT, 2, color);

            maxWidth = Math.max(maxWidth, (int) BAR_WIDTH);
            setHeight(lineHeight + 2 + BAR_HEIGHT + 2);
        } else {
            setHeight(lineHeight);
        }

        setWidth(Math.max(80, maxWidth + 4));
    }

    private String formatMB(long bytes) {
        return String.valueOf(bytes / (1024 * 1024));
    }
}

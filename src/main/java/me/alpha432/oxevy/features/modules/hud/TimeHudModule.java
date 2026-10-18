package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;

public class TimeHudModule extends HudModule {
    public enum TimeFormat {
        T12Hour,
        T24Hour,
        Ticks
    }

    public final Setting<TimeFormat> format = mode("Format", TimeFormat.T24Hour);
    public final Setting<Boolean> showDay = bool("ShowDay", true);

    public TimeHudModule() {
        super("GameTime", "Shows in-game time", 80, 20);
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (nullCheck()) return;

        float x = getX();
        float y = getY();
        GuiGraphics ctx = e.getContext();
        int lineHeight = mc.font.lineHeight;

        long dayTime = mc.level.getDayTime();
        long ticks = dayTime % 24000;
        int hours = (int) ((ticks / 1000 + 6) % 24);
        int minutes = (int) ((ticks % 1000) * 60 / 1000);
        long dayNumber = dayTime / 24000;

        String timeText;
        switch (format.getValue()) {
            case T12Hour -> {
                int h12 = hours % 12;
                if (h12 == 0) h12 = 12;
                String ampm = hours < 12 ? "AM" : "PM";
                timeText = String.format("%d:%02d %s", h12, minutes, ampm);
            }
            case T24Hour -> timeText = String.format("%02d:%02d", hours, minutes);
            case Ticks -> timeText = "Ticks: " + ticks;
            default -> timeText = String.format("%02d:%02d", hours, minutes);
        }

        int maxWidth = 0;

        String line = "§f" + timeText;
        int textWidth = mc.font.width(line);
        ctx.drawString(mc.font, line, (int) (x + 2), (int) y, 0xFFFFFFFF);
        maxWidth = Math.max(maxWidth, textWidth);
        float drawY = y + lineHeight;

        if (showDay.getValue()) {
            String dayLine = "§7Day: §f" + dayNumber;
            int dayWidth = mc.font.width(dayLine);
            ctx.drawString(mc.font, dayLine, (int) (x + 2), (int) drawY, 0xFFFFFFFF);
            maxWidth = Math.max(maxWidth, dayWidth);
            drawY += lineHeight;
        }

        setWidth(Math.max(80, maxWidth + 4));
        setHeight(drawY - y);
    }
}

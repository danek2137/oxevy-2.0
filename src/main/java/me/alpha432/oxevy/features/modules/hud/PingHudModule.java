package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

public class PingHudModule extends HudModule {
    public final Setting<Boolean> showBar = bool("ShowBar", true);
    public final Setting<Boolean> colorCode = bool("ColorCode", true);

    private String cachedText = "";
    private int cachedWidth;
    private int cachedColor = 0xFF44FF44;
    private int lastPing = -1;

    public PingHudModule() {
        super("Ping", "Shows your network latency", 60, 20);
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (mc.player == null || mc.player.connection == null) return;

        int ping = mc.player.connection.getPlayerInfo(mc.player.getUUID()).getLatency();

        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();

        if (ping != lastPing) {
            lastPing = ping;
            cachedText = "Ping: " + ping + "ms";
            cachedWidth = mc.font.width(cachedText);
            if (colorCode.getValue()) {
                if (ping < 80) cachedColor = 0xFF44FF44;
                else if (ping < 150) cachedColor = 0xFFFFFF44;
                else cachedColor = 0xFFFF4444;
            } else {
                cachedColor = 0xFFFFFFFF;
            }
        }

        int textX = (int) x;
        int textY = (int) y;
        ctx.drawString(mc.font, cachedText, textX, textY, cachedColor);

        float totalWidth = cachedWidth;
        float totalHeight = mc.font.lineHeight;

        if (showBar.getValue()) {
            float barWidth = 50;
            float barHeight = 4;
            float barX = x;
            float barY = y + mc.font.lineHeight + 2;

            RenderUtil.rect(ctx, barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
            float clamped = Math.min(ping, 200);
            float fillWidth = (clamped / 200) * barWidth;
            RenderUtil.rect(ctx, barX, barY, barX + fillWidth, barY + barHeight, cachedColor);

            totalWidth = Math.max(totalWidth, barWidth);
            totalHeight = barY + barHeight - y;
        }

        setWidth(totalWidth + 4);
        setHeight(totalHeight + 2);
    }
}

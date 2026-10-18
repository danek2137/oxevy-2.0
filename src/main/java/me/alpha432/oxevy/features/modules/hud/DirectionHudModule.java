package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;

public class DirectionHudModule extends HudModule {

    public final Setting<Boolean> showDegrees = bool("Degrees", true);
    public final Setting<Boolean> showAxis = bool("Axis", true);
    public final Setting<Color> textColor = color("TextColor", new Color(255, 255, 255, 255));

    private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    public DirectionHudModule() {
        super("DirectionHUD", "Shows which direction you are facing", 60, 20);
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (nullCheck()) return;

        GuiGraphics ctx = e.getContext();
        Player player = mc.player;

        float yaw = player.getYRot();
        float yawNormalized = (yaw % 360.0f + 360.0f) % 360.0f;
        int index = (int) Math.floor((yawNormalized + 22.5f) / 45.0f) % 8;
        String dir = DIRECTIONS[index];

        StringBuilder text = new StringBuilder(dir);
        if (showDegrees.getValue()) {
            text.append(" ").append(String.format("%.0f\u00B0", yawNormalized));
        }
        if (showAxis.getValue()) {
            text.append(" ").append(getAxisDirection(yawNormalized));
        }

        String s = text.toString();
        int textWidth = mc.font.width(s);
        setWidth(textWidth + 8);
        setHeight(mc.font.lineHeight + 6);

        float x = getX();
        float y = getY();

        RenderUtil.outlinedRoundRect(ctx, x, y, textWidth + 8, mc.font.lineHeight + 6, 4.0f, 0xFF0D0D0D, 0xFF1C1C22);
        ctx.drawString(mc.font, s, (int) (x + 4), (int) (y + 3), textColor.getValue().getRGB());
    }

    private String getAxisDirection(float yaw) {
        if (yaw >= 315 || yaw < 45) return "+Z";
        if (yaw < 135) return "-X";
        if (yaw < 225) return "-Z";
        return "+X";
    }
}

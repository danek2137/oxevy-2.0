package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.NametagUtils;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LogoutSpotsModule extends Module {

    private static final Color GREEN = new Color(25, 225, 25);
    private static final Color ORANGE = new Color(225, 105, 25);
    private static final Color RED = new Color(225, 25, 25);

    public Setting<Double> scale = num("Scale", 1.0, 0.1, 3.0);
    public Setting<Boolean> fullHeight = bool("FullHeight", true);
    public Setting<Color> sideColor = color("SideColor", 255, 0, 255, 55);
    public Setting<Color> lineColor = color("LineColor", 255, 0, 255, 255);
    public Setting<Color> nameColor = color("NameColor", 255, 255, 255, 255);
    public Setting<Color> nameBgColor = color("NameBgColor", 0, 0, 0, 75);

    private final List<Entry> players = new ArrayList<>();
    private final List<LoggedPlayer> lastPlayers = new ArrayList<>();
    private int timer;

    public LogoutSpotsModule() {
        super("LogoutSpots", "Displays where players logged out", Category.RENDER);
    }

    @Override
    public void onEnable() {
        updateLastPlayers();
        timer = 10;
    }

    @Override
    public void onDisable() {
        players.clear();
        lastPlayers.clear();
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;

        List<LoggedPlayer> currentPlayers = new ArrayList<>();
        for (Player player : mc.level.players()) {
            currentPlayers.add(new LoggedPlayer(player));
        }

        for (LoggedPlayer last : lastPlayers) {
            boolean stillOnline = currentPlayers.stream().anyMatch(p -> p.uuid.equals(last.uuid));
            if (!stillOnline) {
                add(new Entry(last));
            }
        }

        lastPlayers.clear();
        lastPlayers.addAll(currentPlayers);

        if (timer <= 0) {
            timer = 10;
        } else {
            timer--;
        }
    }

    private void updateLastPlayers() {
        lastPlayers.clear();
        for (Player player : mc.level.players()) {
            lastPlayers.add(new LoggedPlayer(player));
        }
    }

    private void add(Entry entry) {
        players.removeIf(p -> p.uuid.equals(entry.uuid));
        players.add(entry);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        for (Entry entry : players) {
            entry.render3D(event);
        }
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        for (Entry entry : players) {
            entry.render2D(event.getContext());
        }
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(players.size());
    }

    private static class LoggedPlayer {
        final UUID uuid;
        final String name;
        final double x, y, z;
        final double width, height;
        final int health;

        LoggedPlayer(Player player) {
            uuid = player.getUUID();
            name = player.getGameProfile().getName();
            x = player.getX();
            y = player.getY();
            z = player.getZ();
            width = player.getBbWidth();
            height = player.getBbHeight();
            health = Math.round(player.getHealth() + player.getAbsorptionAmount());
        }
    }

    private final Vector3d pos = new Vector3d();

    private class Entry {
        final UUID uuid;
        final String name;
        final double x, y, z;
        final double width, height;
        final int health;

        Entry(LoggedPlayer player) {
            uuid = player.uuid;
            name = player.name;
            x = player.x - player.width / 2;
            y = player.y;
            z = player.z - player.width / 2;
            width = player.width;
            height = player.height;
            health = player.health;
        }

        public void render3D(Render3DEvent event) {
            if (fullHeight.getValue()) {
                AABB box = new AABB(x, y, z, x + width, y + height, z + width);
                RenderUtil.drawSolidBoxes(event.getMatrix(), java.util.List.of(box), sideColor.getValue().getRGB(), true);
                RenderUtil.drawOutlinedBoxes(event.getMatrix(), java.util.List.of(box), lineColor.getValue().getRGB(), true);
            } else {
                AABB box = new AABB(x, y, z, x + width, y + 0.01, z + width);
                RenderUtil.drawOutlinedBoxes(event.getMatrix(), java.util.List.of(box), lineColor.getValue().getRGB(), true);
            }
        }

        public void render2D(GuiGraphics ctx) {
            double dist = mc.player.distanceToSqr(x + width / 2, y + height / 2, z + width / 2);
            double viewDist = mc.options.renderDistance().get() * 16;
            if (dist > viewDist * viewDist) return;

            double s = scale.getValue();
            pos.set(x + width / 2, y + height + 0.5, z + width / 2);

            if (!NametagUtils.to2D(pos, s)) return;

            NametagUtils.begin(pos, ctx);

            double healthPct = (double) health / 20.0;
            Color healthColor;
            if (healthPct <= 0.333) healthColor = RED;
            else if (healthPct <= 0.666) healthColor = ORANGE;
            else healthColor = GREEN;

            String text = name + " " + health;
            int textWidth = mc.font.width(text);
            int textHeight = mc.font.lineHeight;

            int bgX = -textWidth / 2 - 4;
            int bgY = -textHeight - 2;
            int bgW = textWidth + 8;
            int bgH = textHeight + 4;

            Color bg = nameBgColor.getValue();
            ctx.fill(bgX, bgY, bgX + bgW, bgY + bgH, bg.getRGB());

            ctx.drawString(mc.font, text, -textWidth / 2, -textHeight, nameColor.getValue().getRGB(), true);

            NametagUtils.end(ctx);
        }
    }
}

package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.input.MouseMoveEvent;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.Deque;

public class MouseMovementHudModule extends HudModule {
    private static final long WINDOW_NS = 10_000_000_000L;   // last 10 seconds
    private static final long SPEED_WINDOW_NS = 500_000_000L; // speed over last 0.5s
    private static final long MIN_SAMPLE_INTERVAL_NS = 10_000_000L; // cap sample rate
    private static final double MIN_SAMPLE_DIST = 0.5;
    private static final float TRAIL_W = 120;
    private static final float TRAIL_H = 46;

    public final Setting<Boolean> showSpeed = bool("Speed", true);
    public final Setting<Boolean> showStats = bool("Stats", true);
    public final Setting<Boolean> showTrail = bool("Trail", true);

    private record Sample(float x, float y, long timeNs) {}

    private final Deque<Sample> samples = new ArrayDeque<>();
    private double virtualX, virtualY;

    public MouseMovementHudModule() {
        super("MouseMovement", "Shows your last 10 seconds of mouse movement", 140, 60);
    }

    @Subscribe
    public void onMouseMove(MouseMoveEvent e) {
        long now = e.getTimeNs();
        Sample last = samples.peekLast();
        if (last != null) {
            long age = now - last.timeNs;
            double dx = e.getDx();
            double dy = e.getDy();
            if (age < MIN_SAMPLE_INTERVAL_NS && dx * dx + dy * dy < MIN_SAMPLE_DIST * MIN_SAMPLE_DIST) {
                return;
            }
        }

        virtualX += e.getDx();
        virtualY += e.getDy();
        samples.addLast(new Sample((float) virtualX, (float) virtualY, now));

        while (samples.size() > 800) {
            samples.removeFirst();
        }
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (nullCheck()) return;

        long now = System.nanoTime();
        pruneSamples(now);

        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();
        int lineHeight = mc.font.lineHeight;

        float totalDist = 0f;
        float recentDist = 0f;
        long anchorTime = -1;
        Sample prev = null;
        for (Sample s : samples) {
            if (prev == null) {
                prev = s;
                continue;
            }
            float d = dist(prev, s);
            totalDist += d;
            if (anchorTime == -1 && s.timeNs >= now - SPEED_WINDOW_NS) {
                anchorTime = prev.timeNs;
            }
            if (anchorTime != -1) {
                recentDist += d;
            }
            prev = s;
        }

        float currentSpeed = 0f;
        if (anchorTime != -1 && now - anchorTime > 0) {
            currentSpeed = recentDist * 1_000_000_000f / (now - anchorTime);
        }

        float drawY = y;
        int maxWidth = 0;

        if (showSpeed.getValue()) {
            String line = "§7Speed: §f" + Math.round(currentSpeed) + " px/s";
            int w = mc.font.width(line);
            ctx.drawString(mc.font, line, (int) (x + 2), (int) drawY, 0xFFFFFFFF);
            maxWidth = Math.max(maxWidth, w);
            drawY += lineHeight;
        }

        if (showStats.getValue()) {
            String line = "§7Distance: §f" + (int) totalDist;
            int w = mc.font.width(line);
            ctx.drawString(mc.font, line, (int) (x + 2), (int) drawY, 0xFFFFFFFF);
            maxWidth = Math.max(maxWidth, w);
            drawY += lineHeight;
        }

        float plotX = x + 6;
        float plotY = drawY + 4;
        float plotW = showTrail.getValue() ? TRAIL_W : 0;
        float plotH = showTrail.getValue() ? TRAIL_H : 0;

        if (showTrail.getValue() && samples.size() >= 2) {
            RenderUtil.rect(ctx, plotX, plotY, plotX + plotW, plotY + plotH, 0x660A0A12);
            RenderUtil.rect(ctx, plotX, plotY, plotX + plotW, plotY + plotH, 0x661E1E2E, 1.0f);
            drawTrail(ctx, plotX, plotY, plotW, plotH);
        }

        float contentW = showTrail.getValue() ? plotW + 12 : maxWidth + 4;
        float contentH = (drawY - y) + (showTrail.getValue() ? plotH + 8 : 0);
        setWidth(Math.max(80, contentW));
        setHeight(contentH);
    }

    private void drawTrail(GuiGraphics ctx, float plotX, float plotY, float plotW, float plotH) {
        Sample[] arr = samples.toArray(new Sample[0]);
        if (arr.length < 2) return;

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Sample s : arr) {
            minX = Math.min(minX, s.x);
            maxX = Math.max(maxX, s.x);
            minY = Math.min(minY, s.y);
            maxY = Math.max(maxY, s.y);
        }

        float rangeX = maxX - minX;
        float rangeY = maxY - minY;
        if (rangeX < 0.1f && rangeY < 0.1f) {
            int cx = Math.round(plotX + plotW / 2f);
            int cy = Math.round(plotY + plotH / 2f);
            ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF00FF66);
            return;
        }

        float scale;
        if (rangeX <= 0.1f) {
            scale = plotH / rangeY * 0.9f;
        } else if (rangeY <= 0.1f) {
            scale = plotW / rangeX * 0.9f;
        } else {
            scale = Math.min(plotW / rangeX, plotH / rangeY) * 0.9f;
        }

        float offX = plotX + (plotW - rangeX * scale) / 2f - minX * scale;
        float offY = plotY + (plotH - rangeY * scale) / 2f - minY * scale;

        int n = arr.length;
        for (int i = 1; i < n; i++) {
            Sample a = arr[i - 1];
            Sample b = arr[i];
            float ax = a.x * scale + offX;
            float ay = a.y * scale + offY;
            float bx = b.x * scale + offX;
            float by = b.y * scale + offY;

            float fade = (float) i / (n - 1);
            int alpha = (int) (30 + 200 * fade);
            int color = (alpha << 24) | 0x00FF66;
            drawSmoothLine(ctx, ax, ay, bx, by, color);

            if (i == n - 1) {
                ctx.fill(Math.round(bx) - 1, Math.round(by) - 1, Math.round(bx) + 1, Math.round(by) + 1, 0xFFFFFFFF);
            }
        }
    }

    private void drawSmoothLine(GuiGraphics ctx, float x1, float y1, float x2, float y2, int color) {
        double dist = Math.hypot(x2 - x1, y2 - y1);
        int steps = Math.max(1, (int) Math.round(dist / 1.5));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            int px = Math.round(x1 + (x2 - x1) * t);
            int py = Math.round(y1 + (y2 - y1) * t);
            ctx.fill(px - 1, py - 1, px + 1, py + 1, color);
        }
    }

    private void pruneSamples(long now) {
        while (!samples.isEmpty() && now - samples.peekFirst().timeNs > WINDOW_NS) {
            samples.removeFirst();
        }
    }

    private float dist(Sample a, Sample b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        return (float) Math.hypot(dx, dy);
    }

    @Override
    public void onDisable() {
        samples.clear();
        virtualX = 0;
        virtualY = 0;
    }
}
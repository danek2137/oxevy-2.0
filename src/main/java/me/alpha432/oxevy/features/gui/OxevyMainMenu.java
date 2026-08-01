package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.*;
import java.awt.Color;
import java.util.Random;

public class OxevyMainMenu extends Screen {

    private final BgParticle[] particles = new BgParticle[65];
    private long lastBgTick = 0;
    private final float[] btnHoverStates = new float[6];

    public OxevyMainMenu() {
        super(Component.literal("Main Menu"));
        Random rng = new Random();
        for (int i = 0; i < particles.length; i++) {
            particles[i] = new BgParticle(rng);
        }
    }

    @Override
    protected void init() {
        for (int i = 0; i < btnHoverStates.length; i++) {
            btnHoverStates[i] = 0.0f;
        }
    }

    private static final String[] BTN_TEXTS = {"Singleplayer", "Multiplayer", "Settings", "Configs", "ClickGUI", "Exit"};
    private static final int BTN_COUNT = 6;

    private void tickBg(float delta) {
        long now = System.currentTimeMillis();
        if (lastBgTick == 0) lastBgTick = now;
        float dt = Math.min((now - lastBgTick) / 1000f, 0.05f);
        lastBgTick = now;

        for (BgParticle p : particles) {
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy -= 1.5f * dt; // gentle drift upwards
            p.alpha += (p.targetAlpha - p.alpha) * dt * 1.5f;

            if (p.alpha < 0.01f) p.alpha = 0.01f;

            // Bounce with boundary checks
            if (p.x < 0) { p.x = 0; p.vx = -p.vx; }
            if (p.x > width) { p.x = width; p.vx = -p.vx; }
            if (p.y < 0) { p.y = 0; p.vy = -p.vy * 0.8f; }
            if (p.y > height) { p.y = height; p.vy = -p.vy * 0.8f; }

            if (--p.twinkleTimer <= 0) {
                p.targetAlpha = 0.05f + rng.nextFloat() * 0.6f;
                p.twinkleTimer = 30 + rng.nextInt(90);
            }
        }
    }

    private static final Random rng = new Random();

    private static class BgParticle {
        float x, y, vx, vy, size, alpha, targetAlpha;
        int twinkleTimer;

        BgParticle(Random r) {
            x = r.nextFloat() * 2000;
            y = r.nextFloat() * 1200;
            vx = (r.nextFloat() - 0.5f) * 18f;
            vy = (r.nextFloat() - 0.5f) * 12f - 4f;
            size = 1.0f + r.nextFloat() * 2.5f;
            alpha = 0.05f + r.nextFloat() * 0.5f;
            targetAlpha = alpha;
            twinkleTimer = r.nextInt(120);
        }
    }

    private void drawLine(GuiGraphics context, float x1, float y1, float x2, float y2, int color, float width) {
        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        bufferBuilder.addVertexWith2DPose(context.pose(), x1, y1).setColor(color).setLineWidth(width);
        bufferBuilder.addVertexWith2DPose(context.pose(), x2, y2).setColor(color).setLineWidth(width);
        me.alpha432.oxevy.util.render.Layers.lines().draw(bufferBuilder.buildOrThrow());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        tickBg(delta);

        // Dark background with vertical gradient
        RenderUtil.verticalGradient(context, 0, 0, width, height, new Color(0x06060C), new Color(0x10101F));

        int accentRGB = ClickGuiModule.getInstance().color.getValue().getRGB();
        int accentRGBNoAlpha = accentRGB & 0xFFFFFF;

        // Draw Plexus Particle lines
        for (int i = 0; i < particles.length; i++) {
            BgParticle p1 = particles[i];
            for (int j = i + 1; j < particles.length; j++) {
                BgParticle p2 = particles[j];
                float dx = p1.x - p2.x;
                float dy = p1.y - p2.y;
                float distSq = dx * dx + dy * dy;
                float maxDist = 95.0f;
                float maxDistSq = maxDist * maxDist;
                if (distSq < maxDistSq) {
                    float dist = (float) Math.sqrt(distSq);
                    float factor = 1.0f - (dist / maxDist);
                    int alphaVal = (int) (factor * 0.16f * 255f * Math.min(p1.alpha, p2.alpha));
                    if (alphaVal > 2) {
                        int lineColor = (alphaVal << 24) | accentRGBNoAlpha;
                        drawLine(context, p1.x, p1.y, p2.x, p2.y, lineColor, 0.8f);
                    }
                }
            }
        }

        // Draw particles
        for (BgParticle p : particles) {
            int a = (int) (p.alpha * 255f);
            if (a < 2) continue;
            int color = (a << 24) | accentRGBNoAlpha;
            RenderUtil.rect(context, p.x, p.y, p.x + p.size, p.y + p.size, color);
            if (p.size > 1.8f) {
                int glowColor = ((a / 4) << 24) | accentRGBNoAlpha;
                float glow = p.size * 3.5f;
                RenderUtil.rect(context, p.x - glow / 2 + p.size / 2, p.y - glow / 2 + p.size / 2,
                    p.x + glow / 2 + p.size / 2, p.y + glow / 2 + p.size / 2, glowColor);
            }
        }

        // Render Sidebar container (Left 28% of the screen)
        int sidebarW = Math.max(220, (int) (width * 0.28f));
        RenderUtil.rect(context, 0, 0, sidebarW, height, 0xE005050A);
        // Frosted glow border right
        RenderUtil.rect(context, sidebarW - 1, 0, sidebarW, height, accentRGB);

        // Logo
        context.pose().pushMatrix();
        float logoScale = 3.2f;
        context.pose().translate(sidebarW / 2.0f, 32);
        context.pose().scale(logoScale, logoScale);
        context.drawString(font, "OXEVY", -font.width("OXEVY") / 2, 0, accentRGB, true);
        context.pose().popMatrix();

        // Subtitle
        String subtitle = "PREMIUM MINECRAFT CLIENT";
        context.drawString(font, subtitle, sidebarW / 2 - font.width(subtitle) / 2, 65, 0xFF888899, false);

        // Divider
        RenderUtil.rect(context, 15, 80, sidebarW - 15, 81, 0x1EFFFFFF);

        // Draw sidebar menu buttons
        int startY = height / 2 - 80;
        int btnX = 15;
        int btnW = sidebarW - 30;
        int btnH = 22;
        int gap = 26;

        for (int i = 0; i < BTN_COUNT; i++) {
            int y = startY + i * gap;
            boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= y && mouseY <= y + btnH;

            // Update hover state animations
            float target = hovered ? 1.0f : 0.0f;
            btnHoverStates[i] += (target - btnHoverStates[i]) * 10f * delta;
            btnHoverStates[i] = Math.max(0.0f, Math.min(1.0f, btnHoverStates[i]));

            // Button card background
            int cardColor = hovered ? 0x22FFFFFF : 0x0AFFFFFF;
            RenderUtil.roundRect(context, btnX, y, btnW, btnH, 4f, cardColor);

            // Left hover line indicator
            int indicatorW = 2 + (int) (btnHoverStates[i] * 3);
            int indicatorColor = ((int) (0xAA + btnHoverStates[i] * 0x55) << 24) | accentRGBNoAlpha;
            RenderUtil.roundRect(context, btnX, y + 2, indicatorW, btnH - 4, 1f, indicatorColor);

            // Text with slide offset
            float textX = btnX + 10 + (btnHoverStates[i] * 5f);
            int textColor = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
            context.drawString(font, BTN_TEXTS[i], (int) textX, y + (btnH - 8) / 2, textColor, false);
        }

        // Sidebar Footer Player Profile
        int footerY = height - 45;
        RenderUtil.rect(context, 15, footerY - 10, sidebarW - 15, footerY - 9, 0x1EFFFFFF);

        String username = minecraft.getUser().getName();
        context.drawString(font, "Welcome, " + username, 20, footerY, 0xFFE2E8F0, false);

        // TODO: Client Update Log (commented out for now)
        // int rightCardX = sidebarW + 30;
        // int rightCardY = 40;
        // int rightCardW = Math.max(260, (int) (width * 0.32f));
        // int changelogH = height - rightCardY - 40;
        // if (changelogH > 80) {
        //     RenderUtil.roundRect(context, rightCardX, rightCardY, rightCardW, changelogH, 6f, 0xAA080812);
        //     RenderUtil.rect(context, rightCardX, rightCardY, rightCardX + rightCardW, rightCardY + 2, accentRGB);
        //     context.drawString(font, "CLIENT UPDATE LOG", rightCardX + 15, rightCardY + 12, accentRGB, false);
        //     String[] logs = {
        //         "- v1.0.0 Release stable build",
        //         "- Brand new modern Sidebar Menu",
        //         "- Particle connection plexus effect",
        //         "- Fixed slider drag-follow bug",
        //         "- Web click GUI updates",
        //         "- Improved bypass optimizations"
        //     };
        //     int textY = rightCardY + 34;
        //     for (String log : logs) {
        //         if (textY + 12 > rightCardY + changelogH - 10) break;
        //         context.drawString(font, log, rightCardX + 15, textY, 0xFF94A3B8, false);
        //         textY += 14;
        //     }
        // }

        // Bottom right watermark info
        String verInfo = "Oxevy Client v1.0.0 | MC 1.21.1";
        context.drawString(font, verInfo, width - font.width(verInfo) - 15, height - 20, 0xFF475569, false);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button == 0) {
            int sidebarW = Math.max(220, (int) (width * 0.28f));
            int startY = height / 2 - 80;
            int btnX = 15;
            int btnW = sidebarW - 30;
            int btnH = 22;
            int gap = 26;

            for (int i = 0; i < BTN_COUNT; i++) {
                int y = startY + i * gap;
                if (isHovered(mouseX, mouseY, btnX, y, btnW, btnH)) {
                    switch (i) {
                        case 0 -> {
                            minecraft.setScreen(new SelectWorldScreen(this));
                            return true;
                        }
                        case 1 -> {
                            minecraft.setScreen(new JoinMultiplayerScreen(this));
                            return true;
                        }
                        case 2 -> {
                            minecraft.setScreen(new OptionsScreen(this, minecraft.options));
                            return true;
                        }
                        case 3 -> {
                            minecraft.setScreen(new ConfigsScreen());
                            return true;
                        }
                        case 4 -> {
                            minecraft.setScreen(ClickGuiScreen.getInstance());
                            return true;
                        }
                        case 5 -> {
                            minecraft.stop();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean isHovered(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}

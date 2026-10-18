package me.alpha432.oxevy.features.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.web.ClickGuiWebBridge;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

public class WebCosmeticsScreen extends Screen {
    private static MCEFBrowser sharedBrowser = null;
    private static boolean browserInitializing = false;

    private final Screen parent;
    private double pxPerGuiX = 1.0;
    private double pxPerGuiY = 1.0;
    private int lastGuiWidth = 0;
    private int lastGuiHeight = 0;
    private int lastBrowserWidth = 0;
    private int lastBrowserHeight = 0;
    private boolean needsResize = true;

    public WebCosmeticsScreen(Screen parent) {
        super(Component.literal("Cosmetics"));
        this.parent = parent;
    }

    private static synchronized void initBrowserIfNeeded(double guiScaleX) {
        if (sharedBrowser != null) return;
        if (browserInitializing) return;
        try {
            String url = ClickGuiWebBridge.ensureStarted() + "cosmetics/"
                    + "?v=" + CosmeticsBuild.V
                    + "#scale=" + Double.toString(guiScaleX) + "&novideo";
            sharedBrowser = MCEFApi.getInstance().createBrowser(url, true);
        } catch (Exception e) {
            browserInitializing = false;
        }
    }

    /** Bumped whenever the cosmetics web bundle changes. */
    private static final class CosmeticsBuild {
        static final String V = "1";
    }

    @Override
    protected void init() {
        double guiScaleX = (double) Minecraft.getInstance().getWindow().getWidth() / (double) Math.max(1, width);
        initBrowserIfNeeded(guiScaleX);
        super.init();
        if (sharedBrowser != null) {
            sharedBrowser.setFocus(true);
        }
    }

    @Override
    public void removed() {
        // Keep shared browser alive across screen switches
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        needsResize = true;
    }

    private void ensureResize(GuiGraphics graphics) {
        if (sharedBrowser == null) return;
        if (width <= 0 || height <= 0) return;

        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        // Only resize when actual GUI dimensions change (not every frame)
        if (lastGuiWidth == guiWidth && lastGuiHeight == guiHeight && !needsResize) {
            return;
        }
        lastGuiWidth = guiWidth;
        lastGuiHeight = guiHeight;
        needsResize = false;

        Minecraft mc = Minecraft.getInstance();
        double fbW = mc.getWindow().getWidth();
        double fbH = mc.getWindow().getHeight();
        double guiW = guiWidth;
        double guiH = guiHeight;
        if (guiW < 1 || guiH < 1) return;

        pxPerGuiX = fbW / guiW;
        pxPerGuiY = fbH / guiH;
        int bw = (int) Math.floor(fbW);
        int bh = (int) Math.floor(fbH);
        // Only resize the CEF browser if dimensions actually changed
        if (bw != lastBrowserWidth || bh != lastBrowserHeight) {
            lastBrowserWidth = bw;
            lastBrowserHeight = bh;
            sharedBrowser.resize(bw, bh);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (sharedBrowser != null) {
            sharedBrowser.onMouseClicked(
                    new MouseButtonEvent((int) (event.x() * pxPerGuiX), (int) (event.y() * pxPerGuiY), event.buttonInfo()),
                    doubleClick);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (sharedBrowser != null) {
            sharedBrowser.onMouseReleased(
                    new MouseButtonEvent((int) (event.x() * pxPerGuiX), (int) (event.y() * pxPerGuiY), event.buttonInfo()));
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (sharedBrowser != null) sharedBrowser.onMouseScrolled((int) (mouseX * pxPerGuiX), (int) (mouseY * pxPerGuiY), scrollY);
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (sharedBrowser != null) sharedBrowser.onMouseMoved((int) (mouseX * pxPerGuiX), (int) (mouseY * pxPerGuiY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (sharedBrowser != null) sharedBrowser.onKeyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_F12 && sharedBrowser != null) {
            try {
                var browserField = sharedBrowser.getClass().getDeclaredField("browser");
                browserField.setAccessible(true);
                var cefBrowser = browserField.get(sharedBrowser);
                if (cefBrowser != null) {
                    var showDevTools = cefBrowser.getClass().getMethod("showDevTools");
                    showDevTools.invoke(cefBrowser);
                }
            } catch (Throwable ignored) {}
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (sharedBrowser != null) sharedBrowser.onKeyReleased(event);
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (sharedBrowser != null) sharedBrowser.onCharTyped(event);
        return super.charTyped(event);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (sharedBrowser == null) {
            graphics.fill(0, 0, width, height, 0x80000000);
            graphics.drawCenteredString(font, "Loading cosmetics...", width / 2, height / 2, 0xFFFFFFFF);
            // Do not call super.render to avoid blur
            return;
        }
        ensureResize(graphics);
        var view = sharedBrowser.getTextureView();
        if (view == null) {
            graphics.fill(0, 0, width, height, 0x80000000);
            graphics.drawCenteredString(font, "Loading...", width / 2, height / 2, 0xFFFFFFFF);
            return;
        }
        try {
            net.minecraft.client.gui.render.TextureSetup setup = net.minecraft.client.gui.render.TextureSetup.singleTexture(view, com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
            graphics.guiRenderState.submitGuiElement(new net.minecraft.client.gui.render.state.BlitRenderState(
                    RenderPipelines.GUI_TEXTURED,
                    setup,
                    new org.joml.Matrix3x2f(graphics.pose()),
                    0, 0, width, height,
                    0.0F, 1.0F, 0.0F, 1.0F,
                    net.minecraft.util.ARGB.colorFromFloat(1.0f, 1.0f, 1.0f, 1.0f),
                    new ScreenRectangle(0, 0, width, height)
            ));
        } catch (Exception ignored) {}
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

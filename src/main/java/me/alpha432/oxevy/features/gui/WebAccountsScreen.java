package me.alpha432.oxevy.features.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
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

public class WebAccountsScreen extends Screen {
    private static MCEFBrowser sharedBrowser = null;
    private static boolean browserInitializing = false;

    private final Screen parent;
    private double pxPerGuiX = 1.0;
    private double pxPerGuiY = 1.0;
    private int lastGuiWidth = 0;
    private int lastGuiHeight = 0;
    private boolean needsResize = true;

    public WebAccountsScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    private static synchronized void initBrowserIfNeeded(double guiScaleX) {
        if (sharedBrowser != null) return;
        if (browserInitializing) return;
        try {
            String url = ClickGuiWebBridge.ensureStarted() + "accounts/"
                    + "?v=" + AccountsBuild.V
                    + "#scale=" + Double.toString(guiScaleX);
            sharedBrowser = MCEFApi.getInstance().createBrowser(url, true);
        } catch (Exception e) {
            browserInitializing = false;
        }
    }

    /** Bumped whenever the accounts web bundle changes. */
    private static final class AccountsBuild {
        static final String V = "11";
    }

    @Override
    protected void init() {
        double guiScaleX = (double) Minecraft.getInstance().getWindow().getWidth() / (double) Math.max(1, width);
        initBrowserIfNeeded(guiScaleX);
        super.init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (sharedBrowser == null) {
            double guiScaleX = (double) Minecraft.getInstance().getWindow().getWidth() / (double) Math.max(1, width);
            if (!browserInitializing) initBrowserIfNeeded(guiScaleX);
            guiGraphics.drawString(Minecraft.getInstance().font, "Loading accounts...", 20, 20, 0xFFAAAAAA);
            return;
        }

        int guiW = width;
        int guiH = height;
        pxPerGuiX = (double) Minecraft.getInstance().getWindow().getWidth() / (double) guiW;
        pxPerGuiY = (double) Minecraft.getInstance().getWindow().getHeight() / (double) guiH;

        if (guiW != lastGuiWidth || guiH != lastGuiHeight) {
            lastGuiWidth = guiW;
            lastGuiHeight = guiH;
            needsResize = true;
        }

        if (needsResize) {
            int physW = (int) Math.round(guiW * pxPerGuiX);
            int physH = (int) Math.round(guiH * pxPerGuiY);
            sharedBrowser.resize(physW, physH);
            needsResize = false;
        }

        var view = sharedBrowser.getTextureView();
        if (view != null) {
            try {
                guiGraphics.guiRenderState.submitGuiElement(new BlitRenderState(
                        RenderPipelines.GUI_TEXTURED,
                        TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                        new Matrix3x2f(guiGraphics.pose()),
                        0, 0, width, height,
                        0.0F, 1.0F, 0.0F, 1.0F,
                        ARGB.colorFromFloat(1.0f, 1.0f, 1.0f, 1.0f),
                        new ScreenRectangle(0, 0, width, height)
                ));
            } catch (Exception ignored) {}
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (sharedBrowser != null) {
            sharedBrowser.onMouseClicked(
                    new MouseButtonEvent(event.x() * pxPerGuiX, event.y() * pxPerGuiY, event.buttonInfo()),
                    doubled);
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (sharedBrowser != null) {
            sharedBrowser.onMouseReleased(
                    new MouseButtonEvent(event.x() * pxPerGuiX, event.y() * pxPerGuiY, event.buttonInfo()));
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (sharedBrowser != null) {
            sharedBrowser.onMouseScrolled((int) (mouseX * pxPerGuiX), (int) (mouseY * pxPerGuiY), verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (sharedBrowser != null) sharedBrowser.onMouseMoved((int) (x * pxPerGuiX), (int) (y * pxPerGuiY));
        super.mouseMoved(x, y);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (sharedBrowser != null) sharedBrowser.onKeyPressed(event);
        // F12: open CEF DevTools for debugging
        if (event.key() == GLFW.GLFW_KEY_F12 && sharedBrowser != null) {
            try {
                // mcef-modern doesn't expose showDevTools() directly.
                // Try reflection to access the underlying CefBrowser.
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
}

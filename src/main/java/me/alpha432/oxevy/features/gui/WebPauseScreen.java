package me.alpha432.oxevy.features.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.web.ClickGuiWebBridge;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.fabricmc.loader.api.FabricLoader;
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

/**
 * Web-based Pause Menu using MCEF browser.
 * Shows profile preview with relog button when in-game.
 */
public class WebPauseScreen extends Screen {

    // Singleton browser instance - kept alive across open/close cycles
    private static MCEFBrowser sharedBrowser = null;
    private static boolean browserInitializing = false;

    private boolean blurrinessCaptured;
    private int previousBlurriness;
    /** Physical framebuffer pixels per GUI-scaled pixel (== guiScale). */
    private double pxPerGuiX = 1.0;
    private double pxPerGuiY = 1.0;

    // Last size/focus state pushed to CEF
    private static int lastBrowserW = -1;
    private static int lastBrowserH = -1;
    private static boolean lastFocused = false;

    /** Pushes size/focus to the browser only when they actually changed. */
    private void syncBrowser(Minecraft mc, boolean focus) {
        if (sharedBrowser == null) return;
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w != lastBrowserW || h != lastBrowserH) {
            lastBrowserW = w;
            lastBrowserH = h;
            sharedBrowser.resize(w, h);
        }
        if (focus != lastFocused) {
            lastFocused = focus;
            sharedBrowser.setFocus(focus);
        }
    }

    // Animation state for fade effects
    private float openProgress = 0.0f;
    private boolean isClosing = false;
    private long animationStartTime = 0;
    private static final int ANIMATION_DURATION_MS = 150; // Faster animation for pause menu

    public static WebPauseScreen instance = null;

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (!FabricLoader.getInstance().isModLoaded("mcef-modern")) {
            Command.sendMessage("mcef-modern is not installed - using native pause menu");
            mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
            return;
        }
        try {
            MCEFApi.Initialization init = MCEFApi.initialize();
            if (!init.isDone()) {
                Command.sendMessage("MCEF is initializing (" + init.getStage() + ") - using native pause menu");
                mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
                return;
            }
        } catch (Throwable t) {
            Command.sendMessage("MCEF init failed: " + t.getMessage() + " - using native pause menu");
            mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
            return;
        }

        // Preload browser in background if not already created
        ensureBrowserInitialized();

        mc.setScreen(new WebPauseScreen());
    }

    /**
     * Ensures the shared browser is initialized.
     */
    public static void ensureBrowserInitialized() {
        if (sharedBrowser != null || browserInitializing) return;

        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(WebPauseScreen::initBrowserInternal);
        } else {
            initBrowserInternal();
        }
    }

    private static void initBrowserInternal() {
        if (sharedBrowser != null || browserInitializing) return;

        browserInitializing = true;
        try {
            MCEFApi.Initialization init = MCEFApi.initialize();
            // Wait for MCEF initialization to complete (with timeout)
            long start = System.currentTimeMillis();
            while (!init.isDone()) {
                if (System.currentTimeMillis() - start > 10_000) {
                    Command.sendMessage("MCEF initialization timed out after 10s");
                    return;
                }
                Thread.sleep(50);
            }

            Minecraft mc = Minecraft.getInstance();
            double guiScaleX = (double) mc.getWindow().getWidth() / mc.getWindow().getGuiScaledWidth();
            String url = ClickGuiWebBridge.ensureStarted() + "pausemenu/" + "#scale=" + Double.toString(guiScaleX);

            sharedBrowser = MCEFApi.getInstance().createBrowser(url, true);
            sharedBrowser.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
            sharedBrowser.setFocus(false);
            // Seed the change-detection cache
            lastBrowserW = mc.getWindow().getWidth();
            lastBrowserH = mc.getWindow().getHeight();
            lastFocused = false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Command.sendMessage("MCEF init interrupted");
        } catch (Throwable t) {
            Command.sendMessage("MCEF init failed: " + t.getMessage());
        } finally {
            browserInitializing = false;
        }
    }

    public static MCEFBrowser getSharedBrowser() {
        return sharedBrowser;
    }

    public static void shutdownBrowser() {
        if (sharedBrowser != null) {
            sharedBrowser.close();
            sharedBrowser = null;
        }
    }

    public WebPauseScreen() {
        super(Component.literal("Game Paused"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();

        // Drive menu blur from ClickGui setting
        if (!blurrinessCaptured) {
            blurrinessCaptured = true;
            previousBlurriness = mc.options.getMenuBackgroundBlurriness();
        }
        int blur = ClickGuiModule.getInstance().blur.getValue();
        if (blur > 0) {
            mc.options.menuBackgroundBlurriness().set(blur);
        }

        // Ensure browser is initialized
        ensureBrowserInitialized();

        // Calculate scale factors for input mapping
        pxPerGuiX = (double) mc.getWindow().getWidth() / width;
        pxPerGuiY = (double) mc.getWindow().getHeight() / height;

        // Start open animation
        openProgress = 1.0f;
        isClosing = false;
        animationStartTime = System.currentTimeMillis();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: transparent browser lets the (blurred) game show through
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Frosted-glass backdrop: mark blur boundary
        if (ClickGuiModule.getInstance().blur.getValue() > 0) {
            try {
                guiGraphics.blurBeforeThisStratum();
            } catch (IllegalStateException ignored) {
            }
        }

        // Ensure browser is initialized and sized
        Minecraft mc = Minecraft.getInstance();
        if (sharedBrowser == null) {
            ensureBrowserInitialized();
            // Draw loading indicator
            guiGraphics.fill(0, 0, width, height, 0xFF000000);
            guiGraphics.drawCenteredString(mc.font, "Loading Pause Menu...", width / 2, height / 2, 0xFFFFFFFF);
            return;
        }

        // Update browser size and focus
        syncBrowser(mc, true);

        // Update animation progress
        updateAnimation(partialTick);

        GpuTextureView view = sharedBrowser.getTextureView();
        if (view != null) {
            try {
                float alpha = isClosing ? openProgress : 1.0f;
                int color = ARGB.colorFromFloat(1.0f, 1.0f, 1.0f, alpha);

                guiGraphics.guiRenderState.submitGuiElement(new BlitRenderState(
                        RenderPipelines.GUI_TEXTURED,
                        TextureSetup.singleTexture(
                                view,
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        ),
                        new Matrix3x2f(guiGraphics.pose()),
                        0,
                        0,
                        width,
                        height,
                        0.0F,
                        1.0F,
                        0.0F,
                        1.0F,
                        color,
                        new ScreenRectangle(0, 0, width, height)
                ));
            } catch (Exception ignored) {
            }
        }
        guiGraphics.requestCursor(sharedBrowser.getCursorType());
    }

    /**
     * Updates the fade animation progress (for closing only).
     */
    private void updateAnimation(float partialTick) {
        if (!isClosing) return;

        long elapsed = System.currentTimeMillis() - animationStartTime;
        float duration = ANIMATION_DURATION_MS;

        // Easing function (cubic ease-out)
        float progress = Math.min(elapsed / duration, 1.0f);
        float eased = 1.0f - (float) Math.pow(1.0 - progress, 3);

        openProgress = 1.0f - eased;
        if (openProgress <= 0.0f) {
            openProgress = 0.0f;
            if (minecraft != null) {
                minecraft.setScreen(null);
            }
        }
    }

    /**
     * Initiates the closing animation.
     */
    private void startClosing() {
        if (!isClosing && openProgress > 0.0f) {
            isClosing = true;
            animationStartTime = System.currentTimeMillis();
        }
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
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            // ESC key resumes the game
            startClosing();
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
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void removed() {
        // DON'T close the browser - keep it alive for next open!
        if (sharedBrowser != null) {
            sharedBrowser.setFocus(false);
            lastFocused = false;
        }

        if (blurrinessCaptured) {
            blurrinessCaptured = false;
            Minecraft.getInstance().options.menuBackgroundBlurriness().set(previousBlurriness);
        }
    }
}
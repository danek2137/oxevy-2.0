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
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

/**
 * WebClickGuiScreen - Preloaded CEF browser ClickGui
 * 
 * The browser is created once on first use and kept alive in the background
 * for instant open/close. The screen just shows/hides the existing browser.
 */
public class WebClickGuiScreen extends Screen {

    // Singleton browser instance - kept alive across open/close cycles
    private static MCEFBrowser sharedBrowser = null;
    private static boolean browserInitializing = false;
    
    private boolean blurrinessCaptured;
    private int previousBlurriness;
    /** Physical framebuffer pixels per GUI-scaled pixel (== guiScale). */
    private double pxPerGuiX = 1.0;
    private double pxPerGuiY = 1.0;

    // Last size/focus state pushed to CEF. resize() forwards to CEF's
    // wasResized(), forcing a full relayout + repaint. Doing that every frame
    // starves CEF's input queue and makes the mouse feel laggy, so only push
    // when the value actually changed.
    private static int lastBrowserW = -1;
    private static int lastBrowserH = -1;
    private static boolean lastFocused = false;

    /**
     * Fraction of the framebuffer the CEF page is rasterised at.
     *
     * <p>mcef-modern drives JCEF offscreen, so the page is rasterised in
     * software on the CPU and uploaded as a texture every time it repaints -
     * cost quadratic in this value. Rasterising the ClickGui at two thirds of
     * the framebuffer cuts both the raster work and the upload bandwidth to
     * ~44%; the blit samples with LINEAR filtering, so for a page made of
     * glass gradients and text the upscale is close to invisible.
     *
     * <p>The page compensates via {@code #scale= guiScale * pageScale} (see
     * initBrowserInternal): its own --s fit clamp divides the now-smaller
     * viewport by the design box, so {@code s} shrinks by exactly the factor
     * the blit later enlarges by, and the ClickGui lands on the same on-screen
     * size as at full resolution.
     *
     * <p>Same contract as WebMainMenuScreen.PAGE_SCALE: never more than 1
     * physical page pixel per GUI pixel, never fewer than 1 page pixel per
     * GUI scale unit.
     */
    private static final double PAGE_SCALE = 2.0 / 3.0;

    /** Effective page scale, fixed when the browser is created so later window
     *  resizes can never desync it from the one-shot {@code #scale=} hint
     *  (the page only reads the fragment once, at load). */
    private static double pageScale = PAGE_SCALE;

    private static double computePageScale(Minecraft mc) {
        int guiScale = Math.max(1, mc.getWindow().getGuiScale());
        return Math.max(PAGE_SCALE, 1.0 / guiScale);
    }

    private static int pageWidth(int framebufferWidth) {
        return Math.max(1, (int) Math.round(framebufferWidth * pageScale));
    }

    private static int pageHeight(int framebufferHeight) {
        return Math.max(1, (int) Math.round(framebufferHeight * pageScale));
    }

    private static void syncBrowser(Minecraft mc, boolean focus) {
        if (sharedBrowser == null) return;
        int w = pageWidth(mc.getWindow().getWidth());
        int h = pageHeight(mc.getWindow().getHeight());
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
    
    // Animation state for slide-from-top effect
    private float openProgress = 0.0f;
    private boolean isClosing = false;
    private long animationStartTime = 0;
    private static final int ANIMATION_DURATION_MS = 300; // 300ms animation

    protected WebClickGuiScreen() {
        super(Component.literal("Oxevy Web ClickGui"));
    }

    /**
     * Opens the built-in browser ClickGui. Falls back to the native ClickGui
     * when mcef-modern is missing or CEF is still initializing.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (!FabricLoader.getInstance().isModLoaded("mcef-modern")) {
            Command.sendMessage("mcef-modern is not installed - using native ClickGui");
            mc.setScreen(ClickGuiScreen.getInstance());
            return;
        }
        try {
            MCEFApi.Initialization init = MCEFApi.initialize();
            if (!init.isDone()) {
                Command.sendMessage("MCEF is initializing (" + init.getStage() + ") - using native ClickGui");
                mc.setScreen(ClickGuiScreen.getInstance());
                return;
            }
        } catch (Throwable t) {
            Command.sendMessage("MCEF init failed: " + t.getMessage() + " - using native ClickGui");
            mc.setScreen(ClickGuiScreen.getInstance());
            return;
        }
        
        // Preload browser in background if not already created
        ensureBrowserInitialized();
        
        mc.setScreen(new WebClickGuiScreen());
    }
    
    /**
     * Ensures the shared browser is initialized (call early, e.g., on mod load).
     */
    public static void ensureBrowserInitialized() {
        if (sharedBrowser != null || browserInitializing) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            // Schedule on client thread
            mc.execute(WebClickGuiScreen::initBrowserInternal);
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
            // Fix the page scale before the page is even loaded: the #scale=
            // hint is read exactly once by the page, so it and every later
            // resize() must agree on it for the rest of the session.
            pageScale = computePageScale(mc);
            double guiScaleX = (double) mc.getWindow().getWidth() / mc.getWindow().getGuiScaledWidth();
            // Feed the page the *effective* device pixels per GUI pixel so its
            // --s fit lands on the same on-screen size as at full resolution
            // even though we rasterise smaller (same trick as the main menu).
            String url = ClickGuiWebBridge.ensureStarted() + "#scale="
                    + Double.toString(guiScaleX * pageScale)
                    // Raster fraction, so the page can tell physical window
                    // pixels from CSS pixels (its compact-mode threshold).
                    + "&raster=" + Double.toString(pageScale);

            int pageW = pageWidth(mc.getWindow().getWidth());
            int pageH = pageHeight(mc.getWindow().getHeight());

            sharedBrowser = MCEFApi.getInstance().createBrowser(url, true);
            sharedBrowser.resize(pageW, pageH);
            sharedBrowser.setFocus(false);
            // Seed the change-detection cache so the first on-screen frame does
            // not redundantly re-issue resize()/setFocus().
            lastBrowserW = pageW;
            lastBrowserH = pageH;
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
    
    /**
     * Gets the shared browser instance.
     */
    public static MCEFBrowser getSharedBrowser() {
        return sharedBrowser;
    }
    
    /**
     * Shuts down the shared browser completely (e.g., on mod unload).
     */
    public static void shutdownBrowser() {
        if (sharedBrowser != null) {
            sharedBrowser.close();
            sharedBrowser = null;
        }
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        
        // The vanilla menu-blur box-blur post chain is triggered by
        // blurBeforeThisStratum(), and its radius comes from the "menu
        // background blurriness" option (read at frame start into the global
        // MenuBlurRadius uniform). Drive it from the ClickGui Blur setting for
        // the lifetime of the screen and restore the player's value on close.
        if (!blurrinessCaptured) {
            blurrinessCaptured = true;
            previousBlurriness = mc.options.getMenuBackgroundBlurriness();
        }
        int blur = ClickGuiModule.getInstance().blur.getValue();
        if (blur > 0) {
            mc.options.menuBackgroundBlurriness().set(blur);
        }
        
        // Use shared browser
        if (sharedBrowser == null) {
            // Browser not ready yet - will be created when ready
            return;
        }
        
        // Update browser size and focus, but only when they actually changed.
        syncBrowser(mc, true);
        
        // Calculate scale factors for input mapping. These convert GUI-space
        // mouse coordinates into *page* pixels CEF expects, so they must use
        // the same pageScale the browser was resized with - raw framebuffer
        // pixels here would offset every click and hover by 1/pageScale.
        pxPerGuiX = (double) mc.getWindow().getWidth() * pageScale / width;
        pxPerGuiY = (double) mc.getWindow().getHeight() * pageScale / height;
        
        // Start open animation
        openProgress = 0.0f;
        isClosing = false;
        animationStartTime = System.currentTimeMillis();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: do not draw the dark dimming gradient vanilla paints behind
        // in-game screens (Screen.renderTransparentBackground fills a ~80%
        // opaque black). The transparent browser texture lets the (blurred)
        // game show through the browser texture instead.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Frosted-glass backdrop: mark the blur boundary so everything drawn
        // below this GUI stratum (world + HUD) is blurred by the vanilla post
        // chain, while the web page draws sharp on top.
        if (ClickGuiModule.getInstance().blur.getValue() > 0) {
            try {
                guiGraphics.blurBeforeThisStratum();
            } catch (IllegalStateException ignored) {
                // blur boundary already marked once this frame
            }
        }
        if (sharedBrowser == null) return;

        boolean animationsEnabled = ClickGuiModule.getInstance().animationsEnabled.getValue();
        if (animationsEnabled) {
            updateAnimation(partialTick);
        } else {
            openProgress = 1.0f;
        }

        GpuTextureView view = sharedBrowser.getTextureView();
        if (view != null) {
            try {
                guiGraphics.pose().pushMatrix();
                if (animationsEnabled) {
                    float slideOffset = (1.0f - openProgress) * height;
                    guiGraphics.pose().translate(0, -slideOffset);
                }
                
                // Apply opacity fade for closing animation
                float alpha = isClosing ? openProgress : 1.0f;
                int color = (int) (alpha * 255) << 24 | 0xFFFFFF;
                
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
                guiGraphics.pose().popMatrix();
            } catch (Exception ignored) {
            }
        }
        guiGraphics.requestCursor(sharedBrowser.getCursorType());
    }

    /**
     * Updates the slide-from-top animation progress.
     */
    private void updateAnimation(float partialTick) {
        long elapsed = System.currentTimeMillis() - animationStartTime;
        float targetProgress = isClosing ? 0.0f : 1.0f;
        float duration = ANIMATION_DURATION_MS;
        
        // Easing function (cubic ease-out)
        float progress = Math.min(elapsed / duration, 1.0f);
        float eased = 1.0f - (float) Math.pow(1.0 - progress, 3);
        
        if (isClosing) {
            openProgress = 1.0f - eased;
            if (openProgress <= 0.0f) {
                openProgress = 0.0f;
                // Close the screen after animation completes
                if (minecraft != null) {
                    minecraft.setScreen(null);
                }
            }
        } else {
            openProgress = eased;
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
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        // While a button is held vanilla calls mouseDragged instead of
        // mouseMoved. Without forwarding it the page never sees a move stream
        // during a drag, so dragging the window or the key-picker header just
        // froze until the button came back up.
        if (sharedBrowser != null) {
            sharedBrowser.onMouseMoved((int) (event.x() * pxPerGuiX), (int) (event.y() * pxPerGuiY));
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (sharedBrowser != null) sharedBrowser.onKeyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (ClickGuiModule.getInstance().animationsEnabled.getValue()) {
                startClosing();
            } else if (minecraft != null) {
                minecraft.setScreen(null);
            }
            return true;
        }
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // DON'T close the browser - keep it alive for next open!
        // Just unfocus it so it doesn't capture input when closed
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

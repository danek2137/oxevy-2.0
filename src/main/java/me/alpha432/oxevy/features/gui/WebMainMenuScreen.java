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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFW;

public class WebMainMenuScreen extends Screen {

    // Singleton browser instance - kept alive across open/close cycles
    private static MCEFBrowser sharedBrowser = null;
    private static boolean browserInitializing = false;

    private boolean blurrinessCaptured;
    private int previousBlurriness;
    /** Physical framebuffer pixels per GUI-scaled pixel (== guiScale). */
    private double pxPerGuiX = 1.0;
    private double pxPerGuiY = 1.0;

    // Last size/focus state pushed to CEF. MCEFBrowser.resize() forwards to
    // CEF's wasResized(), which forces a full relayout + repaint of the page.
    // Doing that every frame starved CEF's input queue and caused severe mouse
    // lag, so we only push when the value actually changed. Static because the
    // browser is a static singleton shared across screen instances.
    // These hold *page* pixels (framebuffer pixels * {@link #PAGE_SCALE}), not
    // framebuffer pixels - see the note on PAGE_SCALE.
    private static int lastBrowserW = -1;
    private static int lastBrowserH = -1;
    private static boolean lastFocused = false;

    /**
     * Fraction of the framebuffer the CEF page is rasterised at.
     *
     * <p>mcef-modern drives JCEF in offscreen (OSR) mode, which rasterises the
     * page <em>in software, on the CPU</em>, then uploads the finished frame as a
     * texture for the blit. Cost is therefore quadratic in this value: at the
     * default full-framebuffer size a 1920x1200 window has ~2.3M pixels
     * rasterised and ~9MB uploaded <em>every frame</em> - and because the menu
     * plays a background video, the page never stops repainting, so that loop
     * runs continuously rather than only on damage.
     *
     * <p>Rendering at two thirds drops both the raster load and the upload
     * bandwidth to ~44%. The blit samples with LINEAR filtering, so the page is
     * simply upscaled - for a layout made of gradients, video and a handful of
     * labels this is close to invisible, and it is what makes the menu
     * responsive again. Lower it further (0.5) for more speed at the cost of
     * softer text.
     *
     * <p>Never scales past 1 physical page pixel per GUI pixel, and never below
     * 1 page pixel per GUI scale unit, so the page's own {@code --s} CSS scaling
     * (which is fed {@code guiScale * pageScale} and clamps at 1) always lands
     * on a consistent on-screen size.
     */
    private static final double PAGE_SCALE = 2.0 / 3.0;

    /** Effective page scale, fixed when the browser is created so that later
     *  window resizes can never desync it from the {@code #scale=} URL hint
     *  (which the page only reads once, at load). */
    private static double pageScale = PAGE_SCALE;

    /**
     * Chooses the page scale for the current GUI scale. At guiScale 1 the page
     * would have to render below 1 page pixel per GUI pixel for the CSS hint to
     * remain valid, so it is left unscaled there; the framebuffer is small in
     * that case anyway.
     */
    private static double computePageScale(Minecraft mc) {
        int guiScale = Math.max(1, mc.getWindow().getGuiScale());
        return Math.max(PAGE_SCALE, 1.0 / guiScale);
    }

    /** Pushes size/focus to the browser only when they actually changed. */
    private void syncBrowser(Minecraft mc, boolean focus) {
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

    private static int pageWidth(int framebufferWidth) {
        return Math.max(1, (int) Math.round(framebufferWidth * pageScale));
    }

    private static int pageHeight(int framebufferHeight) {
        return Math.max(1, (int) Math.round(framebufferHeight * pageScale));
    }

    // Animation state for slide/fade effects
    private float openProgress = 0.0f;
    private boolean isClosing = false;
    private long animationStartTime = 0;
    private static final int ANIMATION_DURATION_MS = 250; // 250ms animation

    public static WebMainMenuScreen instance = null;

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (!FabricLoader.getInstance().isModLoaded("mcef-modern")) {
            Command.sendMessage("mcef-modern is not installed - using native main menu");
            mc.setScreen(new TitleScreen());
            return;
        }
        try {
            MCEFApi.Initialization init = MCEFApi.initialize();
            if (!init.isDone()) {
                Command.sendMessage("MCEF is initializing (" + init.getStage() + ") - using native menu");
                mc.setScreen(new TitleScreen());
                return;
            }
        } catch (Throwable t) {
            Command.sendMessage("MCEF init failed: " + t.getMessage() + " - using native menu");
            mc.setScreen(new TitleScreen());
            return;
        }

        // Preload browser in background if not already created
        ensureBrowserInitialized();

        mc.setScreen(new WebMainMenuScreen());
    }

    /**
     * Ensures the shared browser is initialized (call early, e.g., on mod load).
     */
    public static void ensureBrowserInitialized() {
        if (sharedBrowser != null || browserInitializing) return;

        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(WebMainMenuScreen::initBrowserInternal);
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
            // Fix the page scale now, before the page is even loaded: the
            // #scale= hint is read exactly once by the page, so it and every
            // later resize() must agree on it for the rest of the session.
            pageScale = computePageScale(mc);

            double guiScaleX = (double) mc.getWindow().getWidth() / mc.getWindow().getGuiScaledWidth();
            // Feed the page the *effective* device pixels per GUI pixel, so its
            // --s CSS scaling lands on the same on-screen size as at full
            // resolution even though we rasterise smaller.
            double pageGuiScale = guiScaleX * pageScale;
            String url = ClickGuiWebBridge.ensureStarted() + "mainmenu/" + "#scale=" + Double.toString(pageGuiScale) + "&v=7.3.4";

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

    public static MCEFBrowser getSharedBrowser() {
        return sharedBrowser;
    }

    public static void shutdownBrowser() {
        if (sharedBrowser != null) {
            sharedBrowser.close();
            sharedBrowser = null;
        }
    }

    public WebMainMenuScreen() {
        super(Component.literal("Oxevy Main Menu"));
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

        // Ensure browser is initialized (wait for it if needed)
        ensureBrowserInitialized();

        // Calculate scale factors for input mapping.
        //
        // These convert GUI-space mouse coordinates into the *page* pixels CEF
        // expects, so they must use the same pageScale the browser was resized
        // with - not the raw framebuffer size. Using framebuffer pixels here
        // would offset every click and hover by 1/pageScale.
        pxPerGuiX = (double) mc.getWindow().getWidth() * pageScale / width;
        pxPerGuiY = (double) mc.getWindow().getHeight() * pageScale / height;

        // Start open animation (start at 1.0 = fully visible, no slide-in for main menu)
        openProgress = 1.0f;
        isClosing = false;
        animationStartTime = System.currentTimeMillis();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: do not draw the dark dimming gradient vanilla paints behind
        // in-game screens. The transparent browser texture lets the (blurred)
        // game show through the whole viewport instead.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // NOTE: no blurBeforeThisStratum() here.
        //
        // The pause menu marks a blur boundary so the *world* behind it reads as
        // frosted glass. On the title screen there is no world: renderBackground
        // is a no-op and nothing else is drawn below this stratum, so marking the
        // boundary made the vanilla post chain run a full-screen multi-tap blur
        // over 1920x1200 of blank framebuffer every single frame, for a result
        // that was pixel-identical to not blurring at all. The web page is drawn
        // above the boundary either way, so this costs nothing visually.

        // Ensure browser is initialized and sized
        Minecraft mc = Minecraft.getInstance();
        if (sharedBrowser == null) {
            ensureBrowserInitialized();
            // Draw loading indicator
            guiGraphics.fill(0, 0, width, height, 0xFF000000);
            guiGraphics.drawCenteredString(mc.font, "Loading Oxevy Main Menu...", width / 2, height / 2, 0xFFFFFFFF);
            return;
        }

        // Update browser size and focus, but only when they actually changed.
        // Calling resize()/setFocus() every frame forces CEF to relayout and
        // repaint the whole page at render rate, which backs up the input queue
        // and makes the mouse feel laggy.
        syncBrowser(mc, true);

        // Update animation progress (for closing fade only)
        updateAnimation(partialTick);

        GpuTextureView view = sharedBrowser.getTextureView();
        if (view != null) {
            try {
                // Apply opacity fade for closing animation
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
            // Close the screen after animation completes
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
            // Start closing animation instead of immediately closing
            startClosing();
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
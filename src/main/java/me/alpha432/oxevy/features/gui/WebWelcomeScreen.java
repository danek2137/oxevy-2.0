package me.alpha432.oxevy.features.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.manager.ConfigManager;
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
 * One-time welcome tour, shown the first time the client reaches the title
 * screen after install (see {@code MixinTitleScreen}).
 *
 * <p>It is a web page like every other Oxevy surface: the bridge serves
 * {@code webwelcome/} and this screen blits it. The page's "Get started"
 * button posts to {@code /api/welcome/done}, which marks the tour as seen and
 * calls {@link #requestDismiss()}. Escape does the same from here, so a broken
 * or slow browser can never trap the player behind the intro.
 *
 * <p>The browser instance survives afterwards, exactly like the other web
 * screens, so far more is reused than torn down.
 */
public class WebWelcomeScreen extends Screen {

    // ── Browser (static: one per JVM, reused across open/close) ───────────
    private static MCEFBrowser sharedBrowser = null;
    private static boolean browserInitializing = false;

    /** Set when the tour has been shown once in this JVM, so returning to the
     *  title (quitting a world, disconnecting) cannot replay it. */
    private static boolean shownThisSession = false;

    private static int lastBrowserW = -1;
    private static int lastBrowserH = -1;
    private static boolean lastFocused = false;

    /** See WebMainMenuScreen.PAGE_SCALE: JCEF rasterises offscreen in software,
     *  so the page is drawn at 2/3 of the framebuffer and blitted up. */
    private static final double PAGE_SCALE = 2.0 / 3.0;
    private static double pageScale = PAGE_SCALE;

    private static final int ANIMATION_DURATION_MS = 250;

    private double pxPerGuiX = 1.0;
    private double pxPerGuiY = 1.0;

    private float openProgress = 0.0f;
    private boolean isClosing = false;
    private long animationStartTime = 0;

    public WebWelcomeScreen() {
        super(Component.literal("Welcome to Oxevy"));
        shownThisSession = true;
    }

    /**
     * True while the tour should stand in for the main menu: MCEF is present,
     * the player has never dismissed it before (persisted flag), and it has not
     * already been shown in this session.
     */
    public static boolean shouldShow() {
        if (shownThisSession) return false;
        if (ConfigManager.hasSeenWelcome()) return false;
        return FabricLoader.getInstance().isModLoaded("mcef-modern");
    }

    /** Player is done with the tour: remember it and fade out into the menu. */
    public void dismiss() {
        if (isClosing) return;
        ConfigManager.markWelcomeSeen();
        isClosing = true;
        animationStartTime = System.currentTimeMillis();
    }

    /** Called by the bridge (the page's button) on the client thread. */
    public static void requestDismiss() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WebWelcomeScreen welcome) welcome.dismiss();
    }

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

    /** Pushes size/focus to CEF only when they actually changed. */
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

    private static void ensureBrowserInitialized() {
        if (sharedBrowser != null || browserInitializing) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(WebWelcomeScreen::initBrowserInternal);
        } else {
            initBrowserInternal();
        }
    }

    private static void initBrowserInternal() {
        if (sharedBrowser != null || browserInitializing) return;

        browserInitializing = true;
        try {
            MCEFApi.Initialization init = MCEFApi.initialize();
            long start = System.currentTimeMillis();
            while (!init.isDone()) {
                if (System.currentTimeMillis() - start > 10_000) {
                    Command.sendMessage("MCEF initialization timed out after 10s");
                    return;
                }
                Thread.sleep(50);
            }

            Minecraft mc = Minecraft.getInstance();
            pageScale = computePageScale(mc);

            double guiScaleX = (double) mc.getWindow().getWidth() / mc.getWindow().getGuiScaledWidth();
            String url = ClickGuiWebBridge.ensureStarted() + "welcome/"
                    + "#scale=" + Double.toString(guiScaleX * pageScale);

            int pageW = pageWidth(mc.getWindow().getWidth());
            int pageH = pageHeight(mc.getWindow().getHeight());

            sharedBrowser = MCEFApi.getInstance().createBrowser(url, true);
            sharedBrowser.resize(pageW, pageH);
            sharedBrowser.setFocus(false);
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

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        ensureBrowserInitialized();

        pxPerGuiX = (double) mc.getWindow().getWidth() * pageScale / width;
        pxPerGuiY = (double) mc.getWindow().getHeight() * pageScale / height;

        openProgress = 1.0f;
        isClosing = false;
        animationStartTime = System.currentTimeMillis();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // The page paints its own backdrop; nothing to dim behind it.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Minecraft mc = Minecraft.getInstance();
        if (sharedBrowser == null) {
            ensureBrowserInitialized();
            guiGraphics.fill(0, 0, width, height, 0xFF05070A);
            guiGraphics.drawCenteredString(mc.font, "Loading Oxevy Welcome...", width / 2, height / 2 - 6, 0xFFFFFFFF);
            guiGraphics.drawCenteredString(mc.font, "Press Escape to skip", width / 2, height / 2 + 10, 0xFF8A8F98);
            return;
        }

        syncBrowser(mc, true);
        updateAnimation();

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

    /** Closing fade; hands over to the real main menu once it finishes. */
    private void updateAnimation() {
        if (!isClosing) return;

        long elapsed = System.currentTimeMillis() - animationStartTime;
        float progress = Math.min(elapsed / (float) ANIMATION_DURATION_MS, 1.0f);
        float eased = 1.0f - (float) Math.pow(1.0 - progress, 3);

        openProgress = 1.0f - eased;
        if (openProgress <= 0.0f) {
            openProgress = 0.0f;
            // Hand over exactly once: this screen is on its way out.
            isClosing = false;
            WebMainMenuScreen.open();
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
            dismiss();
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
        // Keep the browser alive for a later reopen; just drop focus.
        if (sharedBrowser != null) {
            sharedBrowser.setFocus(false);
            lastFocused = false;
        }
    }
}

package me.alpha432.oxevy.features.modules.hud;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.web.ClickGuiWebBridge;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/**
 * Apple "Dynamic Island" style HUD widget.
 *
 * <p>The widget is a CEF browser whose texture is blitted into the Minecraft 2D GUI
 * render state. The browser must therefore be sized in <em>physical framebuffer
 * pixels</em> (the widget's GUI size multiplied by the GUI scale), because the
 * blit maps that texture onto {@code islandWidth x islandHeight} <em>GUI</em>
 * pixels. Sizing the browser in GUI pixels instead squeezes the whole page into
 * the widget box.
 *
 * <p>Sizing note: {@code Window#setGuiScale} derives
 * {@code guiScaledWidth = ceil(framebufferWidth / guiScale)}, so the authoritative
 * "physical pixels per GUI pixel" factor is {@link Window#getGuiScale()} - not
 * {@code framebufferWidth / guiScaledWidth}, which drifts by up to a pixel on
 * non-exact divisions. This matches how {@link HudModule#getMouseX()} maps mouse
 * input back into GUI space.
 */
public class DynamicIslandHudModule extends HudModule {

    private static final String LOG_PREFIX = "[DynamicIsland] ";

    /** Fraction of full opacity the widget sits at when not hovered. */
    private static final float IDLE_ALPHA = 0.85f;

    /** Delay before a failed CEF init may be retried. */
    private static final long INIT_RETRY_DELAY_MS = 2000L;

    /** Give up (until the module is toggled) after this many failed attempts. */
    private static final int MAX_INIT_ATTEMPTS = 3;

    private static final float DEFAULT_POS_X = 0.1f;
    private static final float DEFAULT_POS_Y = 0.15f;

    /** Fixed widget size while a track is loaded (matches the web card design). */
    private static final float MUSIC_W = 278.0f;
    private static final float MUSIC_H = 138.0f;

    /** Time constant of the idle<->music size transition, in nanoseconds. */
    private static final double RESIZE_TAU_NS = 90.0e6;

    private MCEFBrowser browser;
    private boolean browserInitialized = false;

    /** Currently applied (animated) widget size in GUI pixels. */
    private float curW = 216.0f;
    private float curH = 26.0f;
    /** Timestamp of the previous frame, for frame-rate independent smoothing. */
    private long lastFrameNs;

    /** Attempts consumed so far; reset on enable/disable. */
    private int initAttempts = 0;
    /** Earliest wall-clock time (ms) at which another init attempt may run. */
    private long nextInitAttemptAtMs = 0L;
    /** True once a chat message has been shown, so it is shown at most once. */
    private boolean reportedInitFailure = false;

    /**
     * Last size pushed to CEF, or {@code -1} when the browser has never been sized.
     * {@code MCEFBrowser#resize} forwards to CEF's {@code wasResized()}, forcing a
     * full relayout + repaint, so it must only be called when the value really changed.
     */
    private int lastBrowserW = -1;
    private int lastBrowserH = -1;
    private static final long MUSIC_SHOW_MS = 5000L;
    private long lastTrackChangeMs = 0L;
    private String lastTrackId = "";
    private boolean musicShowing = false;
    private long musicShowStartMs = 0L;

    // Settings. The seven booleans are read server-side by ClickGuiWebBridge and
    // forwarded to the island page - names and types are part of that contract.
    public final Setting<Boolean> showFps = bool("Show FPS", true);
    public final Setting<Boolean> showPing = bool("Show Ping", false);
    public final Setting<Boolean> showMemory = bool("Show Memory", false);
    public final Setting<Boolean> showCoords = bool("Show Coordinates", true);
    public final Setting<Boolean> showTime = bool("Show Time", true);
    public final Setting<Boolean> showBiome = bool("Show Biome", false);
    public final Setting<Boolean> showDimension = bool("Show Dimension", false);

    public final Setting<Float> islandWidth = num("Width", 216.0f, 150.0f, 400.0f);
    public final Setting<Float> islandHeight = num("Height", 26.0f, 18.0f, 60.0f);
    public final Setting<Float> opacity = num("Opacity", 1.0f, 0.1f, 1.0f);

    public DynamicIslandHudModule() {
        super("DynamicIsland", "Apple Dynamic Island style HUD widget powered by CEF", 216, 26);

        // Better default position: 10% from left, 15% from top (more visible than 10%, 10%)
        pos.setValue(new Vector2f(DEFAULT_POS_X, DEFAULT_POS_Y));
    }

    @Override
    public void onEnable() {
        // Don't init here - wait until first render when world is loaded
        closeBrowser();
        initAttempts = 0;
        nextInitAttemptAtMs = 0L;
        reportedInitFailure = false;

        // Reset position if it's at an invalid/edge position (e.g., saved bad config)
        Vector2f currentPos = pos.getValue();
        if (currentPos.x() < 0.01f || currentPos.y() < 0.01f ||
                currentPos.x() > 0.99f || currentPos.y() > 0.99f) {
            pos.setValue(new Vector2f(DEFAULT_POS_X, DEFAULT_POS_Y));
            Oxevy.LOGGER.info(LOG_PREFIX + "Reset position to default (was: {}, {})", currentPos.x(), currentPos.y());
        }

        // One-time migration: configs saved with any previous default (280x220
        // originally, then 288x35) snap to the current 216x26 pill. Threshold 30
        // covers both earlier defaults even if the setting range clamped them.
        if (islandHeight.getValue() > 30.0f) {
            islandWidth.setValue(216.0f);
            islandHeight.setValue(26.0f);
            Oxevy.LOGGER.info(LOG_PREFIX + "Migrated island size to current default 216x26");
        }

        // The island's now-playing view is fed by the MusicPlayer module's
        // system-media poller, which only runs while that module is enabled.
        // Users should not have to know about that dependency: enabling the
        // island starts the poller. The poller's standalone card stays hidden
        // unless the user turns "Show Card" on, so nothing new pops up on screen.
        try {
            MusicPlayerHudModule music = Oxevy.moduleManager.getModuleByClass(MusicPlayerHudModule.class);
            if (music != null && !music.isEnabled()) {
                music.enable();
                Oxevy.LOGGER.info(LOG_PREFIX + "Started the MusicPlayer poller to feed the island");
            }
        } catch (Throwable t) {
            Oxevy.LOGGER.debug(LOG_PREFIX + "Could not start the MusicPlayer poller", t);
        }
    }

    @Override
    public void onDisable() {
        closeBrowser();
        initAttempts = 0;
        nextInitAttemptAtMs = 0L;
        reportedInitFailure = false;
    }

    private void closeBrowser() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Throwable t) {
                Oxevy.LOGGER.debug(LOG_PREFIX + "Ignoring error while closing browser", t);
            }
            browser = null;
        }
        browserInitialized = false;
        lastBrowserW = -1;
        lastBrowserH = -1;
        lastTrackId = "";
        musicShowing = false;
        lastTrackChangeMs = System.currentTimeMillis();
    }

    /**
     * Lazily creates the browser on first draw (world is guaranteed loaded by then).
     * Retries are rate limited: at most one attempt every {@link #INIT_RETRY_DELAY_MS}
     * and at most {@link #MAX_INIT_ATTEMPTS} in total, so a permanently broken CEF
     * install cannot spam the log or hammer the browser process every frame.
     *
     * @return true if a browser is ready to be drawn
     */
    private boolean ensureBrowser() {
        if (browserInitialized && browser != null) return true;

        long now = System.currentTimeMillis();
        if (now < nextInitAttemptAtMs) return false;
        if (initAttempts >= MAX_INIT_ATTEMPTS) return false;

        initAttempts++;
        nextInitAttemptAtMs = now + INIT_RETRY_DELAY_MS;
        boolean firstAttempt = initAttempts == 1;

        try {
            MCEFApi.Initialization init = MCEFApi.initialize();
            if (init == null || !init.isDone()) {
                String stage = init == null ? "no initialization handle" : String.valueOf(init.getStage());
                logInitFailure(firstAttempt, "MCEF is not ready yet (" + stage + ")", null);
                return false;
            }

            // getInstance() may legitimately return null or throw if MCEF was never
            // loaded (it is an optional mod) - do not let that kill the render loop.
            MCEFApi api = MCEFApi.getInstance();
            if (api == null) {
                logInitFailure(firstAttempt, "MCEF API instance is unavailable", null);
                return false;
            }

            String url = ClickGuiWebBridge.ensureStarted() + "island/";
            browser = api.createBrowser(url, true);
            if (browser == null) {
                logInitFailure(firstAttempt, "MCEF returned a null browser", null);
                return false;
            }
            browser.setFocus(false);

            // Size immediately so the first blit is not of an unsized texture.
            lastBrowserW = -1;
            lastBrowserH = -1;
            syncBrowserSize(islandWidth.getValue(), islandHeight.getValue());

            browserInitialized = true;
            reportedInitFailure = false;
            Oxevy.LOGGER.info(LOG_PREFIX + "Browser created at {} ({}x{} physical)",
                    url, lastBrowserW, lastBrowserH);
            return true;
        } catch (Throwable t) {
            logInitFailure(firstAttempt, "Failed to init CEF: " + t, t);
            // Drop any half-built browser so we do not leak it across retries.
            closeBrowser();
            nextInitAttemptAtMs = System.currentTimeMillis() + INIT_RETRY_DELAY_MS;
            return false;
        }
    }

    /**
     * Logs an init failure once at WARN and thereafter at DEBUG, so a permanently
     * failing install produces one visible log line instead of one per frame.
     */
    private void logInitFailure(boolean firstAttempt, String message, Throwable t) {
        if (firstAttempt) {
            Oxevy.LOGGER.warn(LOG_PREFIX + message
                    + " (attempt 1/" + MAX_INIT_ATTEMPTS + "; retrying every "
                    + INIT_RETRY_DELAY_MS + "ms)", t);
        } else {
            Oxevy.LOGGER.debug(LOG_PREFIX + message
                    + " (attempt " + initAttempts + "/" + MAX_INIT_ATTEMPTS + ")", t);
        }

        if (initAttempts < MAX_INIT_ATTEMPTS || reportedInitFailure) return;
        reportedInitFailure = true;
        Oxevy.LOGGER.warn(LOG_PREFIX + "Giving up on CEF after " + MAX_INIT_ATTEMPTS + " attempts");

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            mc.execute(() -> {
                try {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.displayClientMessage(
                                Component.literal("Dynamic Island: CEF unavailable, widget disabled"), false);
                    }
                } catch (Throwable ignored) {
                    // Never let a chat message take down the render thread.
                }
            });
        } catch (Throwable ignored) {
            // Minecraft instance not ready - logging above is the only notification.
        }
    }

    /**
     * Pushes the widget's physical pixel size to CEF, but only when it differs from
     * the size CEF currently has. Resizing forces a full relayout + repaint, so
     * doing it every frame starves CEF and tanks performance.
     *
     * @param guiW widget width in GUI-scaled pixels
     * @param guiH widget height in GUI-scaled pixels
     */
    private void syncBrowserSize(float guiW, float guiH) {
        if (browser == null) return;

        Window window = Minecraft.getInstance().getWindow();
        if (window == null) return;
        int scale = Math.max(1, window.getGuiScale());

        int physicalW = Math.max(1, (int) Math.ceil((double) guiW * scale));
        int physicalH = Math.max(1, (int) Math.ceil((double) guiH * scale));

        if (physicalW == lastBrowserW && physicalH == lastBrowserH) return;

        // Record before resizing: a throwing resize must not turn into a per-frame retry.
        lastBrowserW = physicalW;
        lastBrowserH = physicalH;
        try {
            browser.resize(physicalW, physicalH);
            Oxevy.LOGGER.debug(LOG_PREFIX + "Resized browser to {}x{} physical", physicalW, physicalH);
        } catch (Throwable t) {
            Oxevy.LOGGER.debug(LOG_PREFIX + "Browser resize failed", t);
        }
    }

    private boolean shouldShowMusic() {
        try {
            MusicPlayerHudModule music = Oxevy.moduleManager.getModuleByClass(MusicPlayerHudModule.class);
            if (music == null) return false;
            MusicPlayerHudModule.NowPlaying np = music.nowPlaying();
            if (np == null || !np.available || !np.hasTrack) {
                musicShowing = false;
                return false;
            }
            String trackId = (np.artist == null ? "" : np.artist) + "|" + (np.title == null ? "" : np.title);
            long now = System.currentTimeMillis();
            if (!trackId.equals(lastTrackId) && !trackId.isEmpty() && !(np.title == null || np.title.isEmpty())) {
                lastTrackId = trackId;
                lastTrackChangeMs = now;
                musicShowing = true;
                musicShowStartMs = now;
                return true;
            }
            if (musicShowing && now - musicShowStartMs < MUSIC_SHOW_MS) {
                return true;
            }
            musicShowing = false;
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Animates curW/curH toward the target size (music card or configured pill)
     * with frame-rate independent exponential smoothing, then applies the result
     * to the module hit box. Returns the animated size.
     */
    private float[] animateSize(float targetW, float targetH) {
        long now = System.nanoTime();
        long dt = lastFrameNs == 0L ? 0L : now - lastFrameNs;
        lastFrameNs = now;
        double alpha = 1.0 - Math.exp(-dt / RESIZE_TAU_NS);
        curW += (targetW - curW) * (float) alpha;
        curH += (targetH - curH) * (float) alpha;
        if (Math.abs(targetW - curW) < 0.5f) curW = targetW;
        if (Math.abs(targetH - curH) < 0.5f) curH = targetH;
        setWidth(curW);
        setHeight(curH);
        return new float[]{curW, curH};
    }

    @Override
    public void drawContent(Render2DEvent e) {
        float targetW = islandWidth.getValue();
        float targetH = islandHeight.getValue();
        if (shouldShowMusic()) {
            targetW = MUSIC_W;
            targetH = MUSIC_H;
        } else {
            musicShowing = false;
        }
        float[] size = animateSize(targetW, targetH);
        float w = size[0];
        float h = size[1];

        if (!ensureBrowser()) return;

        // Track the widget size (and any GUI-scale / window change) every frame.
        // syncBrowserSize is a no-op unless the value actually changed.
        syncBrowserSize(w, h);

        GpuTextureView view;
        try {
            view = browser.getTextureView();
        } catch (Throwable t) {
            Oxevy.LOGGER.debug(LOG_PREFIX + "getTextureView() failed", t);
            return;
        }
        if (view == null) {
            // Normal for the first few frames after creation; not worth logging.
            return;
        }

        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();
        int x0 = (int) x;
        int y0 = (int) y;
        int x1 = (int) (x + w);
        int y1 = (int) (y + h);
        // ScreenRectangle(int, int, int, int) is (x, y, width, height) - its
        // accessors are right() == x + width and bottom() == y + height.
        int scissorW = Math.max(1, x1 - x0);
        int scissorH = Math.max(1, y1 - y0);

        // Single animation source: the base class already drives hoverAnim toward
        // 1.0 on hover / 0.0 off hover, and updateAnimations() runs immediately
        // before drawContent(). Using it avoids the two-value tug-of-war that made
        // the island flicker.
        float alpha = opacity.getValue() * (IDLE_ALPHA + (1.0f - IDLE_ALPHA) * hoverAnim);
        int alphaByte = Math.max(0, Math.min(255, (int) (alpha * 255.0f)));
        int color = ARGB.color(alphaByte, 255, 255, 255);

        ctx.pose().pushMatrix();
        try {
            ctx.guiRenderState.submitGuiElement(new BlitRenderState(
                    RenderPipelines.GUI_TEXTURED,
                    TextureSetup.singleTexture(
                            view,
                            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                    ),
                    new Matrix3x2f(ctx.pose()),
                    x0, y0, x1, y1,
                    0.0F, 1.0F, 0.0F, 1.0F,
                    color,
                    // Scissor is in GUI-scaled screen space, so it has to be the
                    // widget's real on-screen rect - not (0, 0, w, h), which clips
                    // everything outside the top-left corner of the screen.
                    new ScreenRectangle(x0, y0, scissorW, scissorH)
            ));
        } finally {
            ctx.pose().popMatrix();
        }
    }
}
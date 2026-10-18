package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.commands.impl.DestructCommand;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.modules.Module.Category;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Bind;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.KeyboardUtil;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Native ClickGui.
 *
 * <p>Performance notes (per-frame hot path):
 * <ul>
 * <li>Colour interpolation runs through small precomputed LUTs instead of
 * six shift/mask ops + three lerps per module per frame.</li>
 * <li>Text metrics are memoised. {@code Font.width} walks the glyph list, so
 * measuring the same static labels (tabs, footer, hints) every frame was
 * pure waste.</li>
 * <li>Toggle-animation state lives in a primitive {@code float[]} indexed by a
 * dense module id, replacing a {@code HashMap<Module, Float>} that boxed a
 * Float on every read and write.</li>
 * <li>The settings list is rebuilt into a reused {@link ArrayList}, so the
 * render loop allocates nothing per frame.</li>
 * <li>Scroll extents are derived from the full content height in a separate
 * pass rather than from the last visible card, which used to under-report
 * and make the bottom of long lists unreachable.</li>
 * </ul>
 */
public class ClickGuiScreen extends Screen {

    private static ClickGuiScreen INSTANCE;

    // ── Animation state ────────────────────────────────────────────
    private float anim = 1f; // 0 = fully closed, 1 = fully open
    private boolean closing = false; // true while the close animation plays
    private long animStartNs = 0; // when the current anim phase started

    // ── Category-switch animation ──────────────────────────────────
    private float catAnim = 1f; // 0 = just switched, 1 = fully settled
    private long catAnimStartNs = 0;
    private Category prevCategory = null; // category we're sliding away from
    private static final float CAT_ANIM_DURATION = 0.15f; // seconds

    // ── Module switch animation state ──────────────────────────────
    /** Current animation progress per module, indexed by {@link #indexOf(Module)}. */
    private float[] toggleProgress = new float[128];
    /** Dense module id assignment. */
    private final Map<Module, Integer> moduleIndex = new HashMap<>();
    /** Marks an unused slot; false progress so a stale 0f is never mistaken for "animated". */
    private static final float TOGGLE_UNSET = -1f;
    private long lastFrameNs = 0;

    // ── State ──────────────────────────────────────────────────────
    private Category selCategory = Category.COMBAT;
    private Module selModule = null;
    private String searchText = "";
    private boolean searchFocused = false;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int settingsScroll = 0;
    private int settingsMaxScroll = 0;

    private final Map<Setting<?>, SettingComponent> settingComponents = new HashMap<>();
    /** Reused buffer for the currently visible settings, so the render loop allocates nothing. */
    private final List<Setting<?>> visibleSettings = new ArrayList<>();
    private Setting<String> focusedStringSetting = null;
    private Setting<Bind> listeningBindSetting = null;

    private List<Module> cachedFilteredModules = null;
    private String lastFilterQuery = null;
    private Category lastFilterCat = null;

    private boolean destructHolding = false;
    private long destructHoldStartNs = 0;

    // ── Caches (text metrics) ──────────────────────────────────────
    private static final int LUT_STEPS = 64;

    private static int[] TAB_X; // tab left edge, relative to the panel
    private static int[] TAB_W; // tab width
    private static String FOOTER_TEXT;
    private static String HINT_TEXT;
    private static String CLOSE_TEXT;
    private static String DESTRUCT_TEXT;
    private static String DESTRUCT_HOLD_TEXT;
    private static int FOOTER_W, HINT_W, CLOSE_W, DESTRUCT_W, DESTRUCT_HOLD_W;

    private static final Map<String, Integer> WIDTH_CACHE = new HashMap<>();
    private static final Map<Integer, String> BIND_LABEL_CACHE = new HashMap<>();
    private static final int WIDTH_CACHE_CAP = 512;

    // ── Colors ─────────────────────────────────────────────────────
    private static final int C_BG = 0xFF0A0A12;
    private static final int C_PANEL = 0xFF12121E;
    private static final int C_CARD = 0xFF1A1A28;
    private static final int C_CARD_HVR = 0xFF222234;
    private static final int C_INPUT = 0xFF0E0E18;
    private static final int C_ACCENT = 0xFF00FF66;
    private static final int C_ACCENT_RGB = C_ACCENT & 0x00FFFFFF;
    private static final int C_ACCENT2 = 0xFF10B981;
    private static final int C_TXT1 = 0xFFE2E8F0;
    private static final int C_TXT2 = 0xFF94A3B8;
    private static final int C_TXT3 = 0xFF475569;
    private static final int C_SEP = 0xFF1E1E2E;
    private static final int C_TOGGLE_OFF = 0xFF2F2F38;
    private static final int C_DESTRUCT = 0xFF5A0A0A;
    private static final int C_DESTRUCT_HVR = 0xFF7A1414;
    private static final int C_DESTRUCT_ARMED = 0xFFE01414;

    private static final long DESTRUCT_HOLD_MS = 1500L;
    private static final int SEARCH_W = 115;
    private static final int DESTRUCT_BTN_W = 58;
    private static final int DESTRUCT_BTN_H = 24;

    // Interpolation LUTs. Declared after the colour constants above so their
    // initialisers read initialised values (static init runs in textual order).
    /** C_TXT2 -> C_TXT1, for the module name as a module turns on. */
    private static final int[] NAME_COLOR_LUT = buildLut(C_TXT2, C_TXT1);
    /** C_TOGGLE_OFF -> C_ACCENT, for the toggle pill background. */
    private static final int[] PILL_COLOR_LUT = buildLut(C_TOGGLE_OFF, C_ACCENT);

    // ── Layout ─────────────────────────────────────────────────────
    private static final int GUI_W = 620;
    private static final int GUI_H = 420;
    private static final int TAB_H = 36;
    private static final int LEFT_W = 270;
    private static final int CARD_H = 32;
    private static final int CARD_GAP = 4;
    private static final int RIGHT_W = GUI_W - LEFT_W;
    /** Module list viewport, relative to the list panel origin. */
    private static final int LIST_PAD_TOP = 4;
    private static final int LIST_PAD_BOTTOM = 22;

    // ── Per-frame hoisted values (avoid repeated setting lookups) ──
    private boolean animOn;
    private boolean scaleAnim;
    private boolean slideAnim;
    private float toggleDuration = 0.12f;

    private final Minecraft mc = Minecraft.getInstance();

    private ClickGuiScreen() {
        super(Component.literal("Oxevy GUI"));
        ensureStaticLabels();
    }

    // ── Singleton ──────────────────────────────────────────────────
    public static ClickGuiScreen getInstance() {
        if (INSTANCE == null || INSTANCE.mc == null) {
            INSTANCE = new ClickGuiScreen();
        }
        INSTANCE.closing = false;
        INSTANCE.anim = 0f;
        INSTANCE.animStartNs = System.nanoTime();
        INSTANCE.lastFrameNs = System.nanoTime();
        return INSTANCE;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 0.016f : (float) ((now - lastFrameNs) / 1e9);
        lastFrameNs = now;
        dt = Math.min(dt, 0.1f);

        refreshAnimConfig();

        updateAnimation();

        // The close animation finished actually leave the screen.
        if (closing && anim <= 0f) {
            closing = false;
            anim = 0f;
            super.onClose();
            return;
        }

        int guiW = ctx.guiWidth();
        int guiH = ctx.guiHeight();
        int guiX = (guiW - GUI_W) / 2;
        int guiY = (guiH - GUI_H) / 2;

        // backdrop (fades with the animation)
        int alpha = (int) (0xE6 * anim);
        ctx.fill(0, 0, guiW, guiH, (alpha << 24) | 0x050510);

        var pose = ctx.pose();
        pose.pushMatrix();

        float eased = easeOutCubic(anim);
        if (animOn) {
            if (scaleAnim) {
                float scale = 0.9f + 0.1f * eased;
                pose.translate(guiX + GUI_W / 2f, guiY + GUI_H / 2f);
                pose.scale(scale, scale);
                pose.translate(-(guiX + GUI_W / 2f), -(guiY + GUI_H / 2f));
            }
            if (slideAnim) {
                // Slide in from the top of the screen; the close animation
                // reverses automatically (anim goes 1 -> 0) and slides back up.
                float slide = -(1f - eased) * (guiY + GUI_H);
                pose.translate(0f, slide);
            }
        }

        // main bg
        ctx.fill(guiX, guiY, guiX + GUI_W, guiY + GUI_H, C_BG);

        drawTabs(ctx, guiX, guiY, mx, my);
        drawModuleList(ctx, guiX, guiY + TAB_H, mx, my, dt);
        drawSettingsPanel(ctx, guiX + LEFT_W, guiY + TAB_H, mx, my, dt);
        drawFooter(ctx, guiX, guiY + GUI_H);

        pose.popMatrix();

        super.render(ctx, mx, my, delta);
    }

    /** Reads the ClickGui settings once per frame instead of per draw call. */
    private void refreshAnimConfig() {
        ClickGuiModule cgm = ClickGuiModule.getInstance();
        animOn = cgm.animationsEnabled.getValue();
        scaleAnim = cgm.scaleAnimation.getValue();
        slideAnim = cgm.slideInAnimation.getValue();

        // animationSpeed was a per-frame lerp factor; convert it to a duration
        // that stays snappy and can never drag on.
        float speed = Math.max(cgm.animationSpeed.getValue(), 0.01f);
        toggleDuration = Math.max(0.08f, Math.min(0.35f, 0.025f / speed));
    }

    /** Resolves a module to a dense index into {@link #toggleProgress}. */
    private int indexOf(Module mod) {
        Integer cached = moduleIndex.get(mod);
        if (cached != null) return cached;
        int idx = moduleIndex.size();
        if (idx >= toggleProgress.length) {
            toggleProgress = Arrays.copyOf(toggleProgress, Math.max(idx + 1, toggleProgress.length * 2));
        }
        toggleProgress[idx] = TOGGLE_UNSET;
        moduleIndex.put(mod, idx);
        return idx;
    }

    private float getToggleProgress(Module mod, float dt) {
        int idx = indexOf(mod);
        float target = mod.isEnabled() ? 1f : 0f;
        float current = toggleProgress[idx];

        if (current == TOGGLE_UNSET || !animOn) {
            toggleProgress[idx] = target;
            return target;
        }
        if (current != target) {
            current = target > current
                    ? Math.min(target, current + dt / toggleDuration)
                    : Math.max(target, current - dt / toggleDuration);
            toggleProgress[idx] = current;
        }
        return current;
    }

    // ── Animation helpers ─────────────────────────────────────────
    private void updateAnimation() {
        if (!animOn) {
            anim = closing ? 0f : 1f;
            catAnim = 1f;
            return;
        }

        // Deterministic time-based animation: advances linearly toward the
        // target and completes in exactly `animDuration()` seconds. Exponential
        // decay was buggy it never truly reaches the threshold, so the GUI
        // lingered almost-invisible for seconds after closing.
        float duration = animDuration();
        float elapsed = (System.nanoTime() - animStartNs) / 1e9f;
        float t = Math.min(elapsed / duration, 1f);

        anim = closing ? 1f - t : t;

        if (catAnim < 1f) {
            float catDuration = Math.max(0.06f, Math.min(0.4f, 0.035f / Math.max(ClickGuiModule.getInstance().animationSpeed.getValue(), 0.01f)));
            float catElapsed = (System.nanoTime() - catAnimStartNs) / 1e9f;
            catAnim = Math.min(catElapsed / catDuration, 1f);
        }
    }

    private float animDuration() {
        // Clamped so it can never drag on.
        return Math.max(0.06f, Math.min(0.9f, 0.09f / Math.max(ClickGuiModule.getInstance().animationSpeed.getValue(), 0.01f)));
    }

    private static float easeOutCubic(float t) {
        float p = 1f - t;
        return 1f - p * p * p;
    }

    // ── Tabs ───────────────────────────────────────────────────────
    private void drawTabs(GuiGraphics ctx, int gx, int gy, int mx, int my) {
        ctx.fill(gx, gy, gx + GUI_W, gy + TAB_H, C_PANEL);

        Category[] cats = Category.values();
        int tabTop = gy;
        int tabBottom = gy + TAB_H;

        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            int tx = gx + TAB_X[i];
            int tw = TAB_W[i];
            boolean hover = mx >= tx && mx < tx + tw && my >= tabTop && my < tabBottom;
            boolean active = i == selCategory.ordinal();

            if (hover && !active) {
                ctx.fill(tx, tabBottom - 3, tx + tw, tabBottom, 0x4000FF66);
            }

            int textColor = active ? C_ACCENT : (hover ? C_TXT1 : C_TXT2);
            ctx.drawString(CustomFont.font(), cat.getName(), tx + 8, gy + (TAB_H - 8) / 2, textColor);
        }

        // Active indicator line with smooth sliding animation
        int targetX = gx + TAB_X[selCategory.ordinal()];
        int targetW = TAB_W[selCategory.ordinal()];
        if (animOn && prevCategory != null && catAnim < 1f) {
            float eased = easeOutCubic(catAnim);
            int prevX = gx + TAB_X[prevCategory.ordinal()];
            int prevW = TAB_W[prevCategory.ordinal()];

            float curX = prevX + (targetX - prevX) * eased;
            float curW = prevW + (targetW - prevW) * eased;
            RenderUtil.rect(ctx, curX, gy + TAB_H - 3, curX + curW, gy + TAB_H, C_ACCENT);
        } else {
            ctx.fill(targetX, gy + TAB_H - 3, targetX + targetW, gy + TAB_H, C_ACCENT);
        }

        // Destruct button (small, placed right after search on top bar)
        int dx = gx + GUI_W - DESTRUCT_BTN_W - 8;
        int dy = gy + 6;
        drawDestructButton(ctx, dx, dy, mx, my);

        // search (to the left of destruct)
        int sx = dx - SEARCH_W - 6;
        ctx.fill(sx, gy + 6, sx + SEARCH_W, gy + TAB_H - 6, C_INPUT);
        boolean emptyPrompt = searchText.isEmpty() && !searchFocused;
        ctx.drawString(CustomFont.font(), emptyPrompt ? HINT_TEXT : searchText,
                sx + 6, gy + (TAB_H - 8) / 2, emptyPrompt ? C_TXT3 : C_TXT1);

        if (searchFocused) {
            int cursorW = textWidth(searchText);
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                ctx.fill(sx + 7 + cursorW, gy + 10, sx + 8 + cursorW, gy + 20, C_ACCENT);
            }
        }
    }

    // ── Module List ────────────────────────────────────────────────
    private void drawModuleList(GuiGraphics ctx, int gx, int gy, int mx, int my, float dt) {
        int listTop = gy;
        int listBottom = gy + GUI_H - TAB_H;
        int listLeft = gx;
        int listRight = gx + LEFT_W;

        // panel bg + separator
        ctx.fill(listLeft, listTop, listRight, listBottom, C_PANEL);
        ctx.fill(listRight - 1, listTop, listRight, listBottom, C_SEP);

        List<Module> mods = getFilteredModules();
        int cardsTop = listTop + LIST_PAD_TOP;
        int contentBottom = listBottom - LIST_PAD_BOTTOM;
        int visibleH = contentBottom - cardsTop;

        // Full content extent, independent of what is actually on screen.
        int totalH = mods.isEmpty() ? 0 : mods.size() * (CARD_H + CARD_GAP) - CARD_GAP;
        maxScroll = Math.max(0, totalH - visibleH);

        int cardLeft = listLeft + 4;
        int cardW = LEFT_W - 8;
        int cardRight = cardLeft + cardW;

        ctx.enableScissor(listLeft, listTop, listRight, contentBottom);

        var pose = ctx.pose();
        pose.pushMatrix();

        float slideX = 0f;
        if (animOn && catAnim < 1f) {
            float eased = easeOutCubic(catAnim);
            int dir = (prevCategory != null) ? Integer.compare(selCategory.ordinal(), prevCategory.ordinal()) : 1;
            if (dir == 0) dir = 1;
            slideX = (1f - eased) * 24f * dir;
            pose.translate(slideX, 0f);
        }
        int effMx = (int) (mx - slideX);

        int yOff = cardsTop - scrollOffset;

        for (int i = 0; i < mods.size(); i++) {
            Module mod = mods.get(i);
            if (yOff + CARD_H < listTop) { yOff += CARD_H + CARD_GAP; continue; }
            if (yOff > contentBottom) break;

            boolean hover = effMx >= cardLeft && effMx < cardRight
                    && my >= yOff && my < yOff + CARD_H
                    && my >= listTop && my < contentBottom;

            ctx.fill(cardLeft, yOff, cardRight, yOff + CARD_H,
                    (hover || mod == selModule) ? C_CARD_HVR : C_CARD);

            float switchProgress = getToggleProgress(mod, dt);
            float p = switchProgress * switchProgress * (3f - 2f * switchProgress);
            int lut = lutIndex(p);

            // accent bar when enabled (fades in/out smoothly)
            if (p > 0.01f) {
                ctx.fill(cardLeft, yOff, cardLeft + 3, yOff + CARD_H,
                        ((int) (p * 255) << 24) | C_ACCENT_RGB);
            }

            // name color smoothly blends from C_TXT2 to C_TXT1
            ctx.drawString(CustomFont.font(), mod.getDisplayName(),
                    cardLeft + 10, yOff + (CARD_H - 8) / 2, NAME_COLOR_LUT[lut]);

            // bind badge
            Bind bind = mod.getBind();
            if (!bind.isEmpty()) {
                String bindStr = bindLabel(bind.getKey());
                int bw = textWidth(bindStr) + 8;
                int bx = cardRight - bw - 34;
                ctx.fill(bx, yOff + 9, bx + bw, yOff + CARD_H - 9, C_BG);
                ctx.drawString(CustomFont.font(), bindStr, bx + 4, yOff + (CARD_H - 8) / 2, C_ACCENT2);
            }

            // toggle pill with animated sliding knob and smooth background transition
            int px = cardRight - 30;
            int py = yOff + (CARD_H - 14) / 2;
            ctx.fill(px, py, px + 26, py + 14, PILL_COLOR_LUT[lut]);
            ctx.fill(px + 2 + (int) (12f * p), py + 2, px + 12 + (int) (12f * p), py + 12, C_TXT1);

            yOff += CARD_H + CARD_GAP;
        }

        pose.popMatrix();
        ctx.disableScissor();
    }

    // ── Destruct Button ───────────────────────────────────────────
    private void drawDestructButton(GuiGraphics ctx, int dx, int dy, int mx, int my) {
        boolean hover = mx >= dx && mx < dx + DESTRUCT_BTN_W && my >= dy && my < dy + DESTRUCT_BTN_H;

        if (destructHolding) {
            if (!hover) {
                destructHolding = false;
            } else if (System.nanoTime() - destructHoldStartNs >= DESTRUCT_HOLD_MS * 1_000_000L) {
                destructHolding = false;
                DestructCommand.destruct();
            }
        }

        ctx.fill(dx, dy, dx + DESTRUCT_BTN_W, dy + DESTRUCT_BTN_H, hover ? C_DESTRUCT_HVR : C_DESTRUCT);

        String label;
        int labelW;
        int txt;
        if (destructHolding) {
            float progress = (float) (System.nanoTime() - destructHoldStartNs) / (DESTRUCT_HOLD_MS * 1_000_000f);
            int fillW = (int) (DESTRUCT_BTN_W * Math.min(1f, progress));
            if (fillW > 0) {
                ctx.fill(dx, dy, dx + fillW, dy + DESTRUCT_BTN_H, C_DESTRUCT_ARMED);
            }
            label = DESTRUCT_HOLD_TEXT;
            labelW = DESTRUCT_HOLD_W;
            txt = 0xFFFFFFFF;
        } else {
            label = DESTRUCT_TEXT;
            labelW = DESTRUCT_W;
            txt = 0xFFF0B0B0;
        }
        ctx.drawString(CustomFont.font(), label, dx + (DESTRUCT_BTN_W - labelW) / 2, dy + (DESTRUCT_BTN_H - 8) / 2, txt);
    }

    // ── Settings Panel ─────────────────────────────────────────────
    private void drawSettingsPanel(GuiGraphics ctx, int px, int py, int mx, int my, float dt) {
        int pRight = px + RIGHT_W;
        int pBottom = py + GUI_H - TAB_H;

        ctx.fill(px, py, pRight, pBottom, C_PANEL);

        if (selModule == null) {
            ctx.drawString(CustomFont.font(), HINT_TEXT,
                    px + (RIGHT_W - HINT_W) / 2,
                    py + (GUI_H - TAB_H - 8) / 2,
                    C_TXT3);
            return;
        }

        // header
        ctx.drawString(CustomFont.font(), selModule.getDisplayName(), px + 12, py + 10, C_TXT1);
        ctx.fill(px + 8, py + 24, pRight - 8, py + 25, C_SEP);

        // close hint
        ctx.drawString(CustomFont.font(), CLOSE_TEXT, pRight - 8 - CLOSE_W, py + 10, C_TXT3);

        // module enable/disable switch (matches the module list pill)
        int swW = 26;
        int swH = 14;
        int swX = pRight - 8 - CLOSE_W - 6 - swW;
        int swY = py + (24 - swH) / 2;
        boolean swHover = mx >= swX && mx < swX + swW && my >= swY && my < swY + swH;
        float p = getToggleProgress(selModule, dt);
        int lut = lutIndex(p);
        ctx.fill(swX, swY, swX + swW, swY + swH, (swHover ? 0x22FFFFFF : 0) | PILL_COLOR_LUT[lut]);
        ctx.fill(swX + 2 + (int) (12f * p), swY + 2, swX + 12 + (int) (12f * p), swY + 12, C_TXT1);

        // settings list
        int listTop = py + 32;
        int listBottom = pBottom - 4;
        int compX = px + 12;
        int compW = RIGHT_W - 24;
        int firstY = listTop + 2 - settingsScroll;

        List<Setting<?>> settings = collectVisibleSettings(selModule);

        // Pass 1: position every component and accumulate the full content
        // height, so the scroll extent does not depend on the visible window.
        int totalH = 0;
        for (int i = 0; i < settings.size(); i++) {
            SettingComponent comp = getOrCreateComponent(settings.get(i), compX, firstY + totalH, compW);
            totalH += comp.getHeight() + 4;
        }
        settingsMaxScroll = Math.max(0, totalH - (listBottom - firstY) + 8);

        // Pass 2: draw the ones actually in view.
        ctx.enableScissor(px, listTop, pRight, listBottom);
        for (int i = 0; i < settings.size(); i++) {
            SettingComponent comp = settingComponents.get(settings.get(i));
            if (comp.y + comp.getHeight() + 4 < listTop) continue;
            if (comp.y > listBottom) break;
            comp.render(ctx, mx, my, 0);
        }
        ctx.disableScissor();
    }

    /**
     * Fills {@link #visibleSettings} with the settings of {@code mod} that are
     * worth showing. Reuses the buffer so the render loop stays allocation-free;
     * rebuilding each frame (rather than caching) keeps conditional visibility
     * correct the instant a setting is toggled.
     */
    private List<Setting<?>> collectVisibleSettings(Module mod) {
        visibleSettings.clear();
        for (Setting<?> s : mod.getSettings()) {
            if (s.isVisible() && !"Enabled".equals(s.getName()) && !"DisplayName".equals(s.getName())) {
                visibleSettings.add(s);
            }
        }
        return visibleSettings;
    }

    // ── Footer ─────────────────────────────────────────────────────
    private void drawFooter(GuiGraphics ctx, int gx, int gyBottom) {
        int fy = gyBottom - 18;
        ctx.fill(gx, fy, gx + GUI_W, gyBottom, C_PANEL);
        ctx.drawString(CustomFont.font(), FOOTER_TEXT, gx + (GUI_W - FOOTER_W) / 2, fy + 5, C_TXT3);
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (closing) return true;
        int mx = (int) click.x();
        int my = (int) click.y();
        int btn = click.button();
        int guiX = (mc.getWindow().getGuiScaledWidth() - GUI_W) / 2;
        int guiY = (mc.getWindow().getGuiScaledHeight() - GUI_H) / 2;

        // clear focus
        focusedStringSetting = null;
        listeningBindSetting = null;
        for (SettingComponent c : settingComponents.values()) {
            if (c instanceof BindComponent bc) bc.stopListening();
            if (c instanceof DropdownComponent dc && !dc.isHovered(mx, my)) dc.close();
        }

        // tab clicks
        if (my >= guiY && my < guiY + TAB_H) {
            destructHolding = false;

            // destruct click (top bar, right of search)
            int dx = guiX + GUI_W - DESTRUCT_BTN_W - 8;
            int dy = guiY + 6;
            if (mx >= dx && mx < dx + DESTRUCT_BTN_W && my >= dy && my < dy + DESTRUCT_BTN_H) {
                if (btn == 0) handleDestructClick();
                return true;
            }

            // search click
            int sx = dx - SEARCH_W - 6;
            if (mx >= sx && mx < sx + SEARCH_W && my >= dy && my < dy + DESTRUCT_BTN_H) {
                searchFocused = true;
                return true;
            }
            searchFocused = false;

            for (int i = 0; i < TAB_X.length; i++) {
                int tx = guiX + TAB_X[i];
                if (mx >= tx && mx < tx + TAB_W[i]) {
                    Category cat = Category.values()[i];
                    if (selCategory != cat) {
                        prevCategory = selCategory;
                        selCategory = cat;
                        selModule = null;
                        scrollOffset = 0;
                        settingsScroll = 0;
                        settingComponents.clear();
                        catAnim = 0f;
                        catAnimStartNs = System.nanoTime();
                    }
                    return true;
                }
            }
            return true;
        }

        int listTop = guiY + TAB_H;
        int listBottom = guiY + GUI_H;
        int listLeft = guiX;
        int listRight = guiX + LEFT_W;

        // module list clicks
        if (mx >= listLeft && mx < listRight && my >= listTop && my < listBottom - LIST_PAD_BOTTOM) {
            searchFocused = false;
            destructHolding = false;
            List<Module> mods = getFilteredModules();
            int cardW = LEFT_W - 8;
            int cardLeft = listLeft + 4;
            int cardRight = cardLeft + cardW;
            int toggleLeft = cardRight - 30;
            int yOff = listTop + LIST_PAD_TOP - scrollOffset;

            float slideX = 0f;
            if (animOn && catAnim < 1f) {
                float eased = easeOutCubic(catAnim);
                int dir = (prevCategory != null) ? Integer.compare(selCategory.ordinal(), prevCategory.ordinal()) : 1;
                if (dir == 0) dir = 1;
                slideX = (1f - eased) * 24f * dir;
            }
            int effMx = (int) (mx - slideX);

            for (Module mod : mods) {
                if (my >= yOff && my < yOff + CARD_H && effMx >= cardLeft && effMx < cardRight) {
                    if (btn == 0 && effMx >= toggleLeft && effMx < toggleLeft + 26) {
                        mod.toggle();
                        return true;
                    }
                    if (btn == 1 || btn == 0) {
                        selectModule(selModule == mod ? null : mod);
                        return true;
                    }
                }
                yOff += CARD_H + CARD_GAP;
            }
            return true;
        }

        // settings panel clicks
        int settingsLeft = guiX + LEFT_W;
        int settingsRight = guiX + GUI_W;
        if (mx >= settingsLeft && mx < settingsRight && my >= listTop && my < listBottom - LIST_PAD_BOTTOM) {
            if (selModule == null) return true;

            // module enable/disable switch (header, left of [ESC])
            int swW = 26;
            int swH = 14;
            int swX = settingsRight - 8 - CLOSE_W - 6 - swW;
            int swY = listTop + (24 - swH) / 2;
            if (mx >= swX && mx < swX + swW && my >= swY && my < swY + swH) {
                selModule.toggle();
                return true;
            }

            // check close button
            int closeX = settingsRight - 8 - CLOSE_W;
            if (mx >= closeX && mx < closeX + CLOSE_W && my >= listTop + 4 && my < listTop + 22) {
                selectModule(null);
                return true;
            }

            int compTop = listTop + 32;
            int compBottom = listBottom - LIST_PAD_BOTTOM;
            int compW = RIGHT_W - 24;
            int sy = compTop + 2 - settingsScroll;

            for (Setting<?> setting : collectVisibleSettings(selModule)) {
                SettingComponent comp = getOrCreateComponent(setting, settingsLeft + 12, sy, compW);
                int h = comp.getHeight() + 4;
                if (sy + h < compTop) { sy += h; continue; }
                if (sy > compBottom) break;

                if (comp.isHovered(mx, my)) {
                    comp.mouseClicked(mx, my, btn);
                    if (comp instanceof StringComponent sc && sc.isFocused()) {
                        focusedStringSetting = (Setting<String>) setting;
                    }
                    if (comp instanceof BindComponent bc && bc.isListening()) {
                        listeningBindSetting = (Setting<Bind>) setting;
                    }
                    return true;
                }
                sy += h;
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    /** Selecting/deselecting a module resets its panel scroll and editor state. */
    private void selectModule(Module mod) {
        selModule = mod;
        settingComponents.clear();
        focusedStringSetting = null;
        listeningBindSetting = null;
        settingsScroll = 0;
    }

    private void handleDestructClick() {
        if (destructHolding) return;
        destructHolding = true;
        destructHoldStartNs = System.nanoTime();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        destructHolding = false;
        for (SettingComponent comp : settingComponents.values()) {
            comp.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int guiX = (mc.getWindow().getGuiScaledWidth() - GUI_W) / 2;
        int guiY = (mc.getWindow().getGuiScaledHeight() - GUI_H) / 2;

        // settings panel scroll
        if (mx >= guiX + LEFT_W && mx < guiX + GUI_W && my >= guiY + TAB_H && my < guiY + GUI_H - LIST_PAD_BOTTOM) {
            settingsScroll = clampScroll(settingsScroll - vertical * 16, settingsMaxScroll);
            return true;
        }
        // module list scroll
        if (mx >= guiX && mx < guiX + LEFT_W && my >= guiY + TAB_H && my < guiY + GUI_H - LIST_PAD_BOTTOM) {
            scrollOffset = clampScroll(scrollOffset - vertical * 16, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private static int clampScroll(double value, int max) {
        if (value <= 0) return 0;
        int v = (int) value;
        return Math.min(v, max);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();

        if (listeningBindSetting != null) {
            Bind val = key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE ? Bind.none() : new Bind(key);
            listeningBindSetting.setValue(val);
            listeningBindSetting = null;
            for (SettingComponent c : settingComponents.values()) {
                if (c instanceof BindComponent bc) bc.stopListening();
            }
            return true;
        }

        if (focusedStringSetting != null) {
            switch (key) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,
                     org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,
                     org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER -> focusedStringSetting = null;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                    String s = focusedStringSetting.getValue();
                    if (!s.isEmpty()) focusedStringSetting.setValue(s.substring(0, s.length() - 1));
                }
            }
            return true;
        }

        if (searchFocused) {
            switch (key) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length() - 1);
                    scrollOffset = 0;
                    return true;
                }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE -> { searchText = ""; scrollOffset = 0; return true; }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> { searchFocused = false; return true; }
            }
            return true;
        }

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (selModule != null) {
                selectModule(null);
            } else {
                onClose();
            }
            return true;
        }

        for (SettingComponent c : settingComponents.values()) {
            if (c.isCapturingInput() && c.keyPressed(input)) return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        String chr = input.codepointAsString();
        if (chr.isEmpty()) return super.charTyped(input);

        if (focusedStringSetting != null) {
            String cur = focusedStringSetting.getValue();
            if (cur.length() < 64) focusedStringSetting.setValue(cur + chr);
            return true;
        }
        if (searchFocused) {
            if (searchText.length() < 64) { searchText += chr; scrollOffset = 0; }
            return true;
        }
        return super.charTyped(input);
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private List<Module> getFilteredModules() {
        if (cachedFilteredModules != null && selCategory == lastFilterCat && searchText.equals(lastFilterQuery)) {
            return cachedFilteredModules;
        }
        lastFilterCat = selCategory;
        lastFilterQuery = searchText;

        List<Module> list = new ArrayList<>();
        if (searchText.isBlank()) {
            for (Module m : Oxevy.moduleManager.getModules()) {
                if (m.getCategory() == selCategory && !m.hidden) list.add(m);
            }
        } else {
            String lower = searchText.toLowerCase();
            for (Module m : Oxevy.moduleManager.getModules()) {
                if (!m.hidden && m.getDisplayName().toLowerCase().contains(lower)) list.add(m);
            }
        }
        cachedFilteredModules = list;
        return list;
    }

    private SettingComponent getOrCreateComponent(Setting<?> setting, int x, int y, int w) {
        SettingComponent comp = settingComponents.get(setting);
        if (comp == null) {
            comp = createComponent(setting, x, y, w);
            settingComponents.put(setting, comp);
        } else {
            comp.x = x;
            comp.y = y;
        }
        return comp;
    }

    @SuppressWarnings("unchecked")
    private SettingComponent createComponent(Setting<?> setting, int x, int y, int w) {
        int h = 16;
        Object val = setting.getValue();
        if (val instanceof Boolean) return new BooleanComponent((Setting<Boolean>) setting, x, y, w, h);
        if (val instanceof Number) {
            if (setting.getMin() != null) return new SliderComponent((Setting<Number>) setting, x, y, w, h);
            return new StringSettingComponent(setting, x, y, w, h);
        }
        if (val instanceof Bind) return new BindComponent((Setting<Bind>) setting, x, y, w, h);
        if (val instanceof Color) return new ColorComponent((Setting<Color>) setting, x, y, w, h);
        if (setting.isEnumSetting()) return new DropdownComponent(setting, x, y, w, h);
        if (setting.isStringSetting()) return new StringComponent((Setting<String>) setting, x, y, w, h);
        if (setting.isButtonSetting()) return new ButtonComponent((Setting<Runnable>) setting, x, y, w, h);
        return new StringSettingComponent(setting, x, y, w, h);
    }

    // ── Text metrics / colour LUTs ─────────────────────────────────

    /** Cached {@code Font.width}; the glyph walk is too costly for a render loop. */
    private static int textWidth(String s) {
        if (s == null || s.isEmpty()) return 0;
        Integer cached = WIDTH_CACHE.get(s);
        if (cached != null) return cached;
        int w = CustomFont.font().width(s);
        // Bound the cache so renamed modules / a burst of unique bind labels
        // cannot keep growing it forever.
        if (WIDTH_CACHE.size() > WIDTH_CACHE_CAP) WIDTH_CACHE.clear();
        WIDTH_CACHE.put(s, w);
        return w;
    }

    private static String bindLabel(int key) {
        String cached = BIND_LABEL_CACHE.get(key);
        if (cached != null) return cached;
        String label = KeyboardUtil.getKeyName(key);
        if (BIND_LABEL_CACHE.size() > 256) BIND_LABEL_CACHE.clear();
        BIND_LABEL_CACHE.put(key, label);
        return label;
    }

    /** Computes the static tab strip and label metrics exactly once. */
    private static void ensureStaticLabels() {
        if (TAB_X != null) return;

        Category[] cats = Category.values();
        TAB_X = new int[cats.length];
        TAB_W = new int[cats.length];
        int x = 8;
        for (int i = 0; i < cats.length; i++) {
            TAB_W[i] = textWidth(cats[i].getName()) + 16;
            TAB_X[i] = x;
            x += TAB_W[i] + 4;
        }

        HINT_TEXT = "Right-click a module to edit settings";
        HINT_W = textWidth(HINT_TEXT);
        CLOSE_TEXT = "[ESC]";
        CLOSE_W = textWidth(CLOSE_TEXT);
        FOOTER_TEXT = "LMB: Select | Toggle: Enable/Disable | Scroll: Navigate | ESC: Close";
        FOOTER_W = textWidth(FOOTER_TEXT);
        DESTRUCT_TEXT = "DESTRUCT";
        DESTRUCT_W = textWidth(DESTRUCT_TEXT);
        DESTRUCT_HOLD_TEXT = "HOLD";
        DESTRUCT_HOLD_W = textWidth(DESTRUCT_HOLD_TEXT);
    }

    private static int[] buildLut(int from, int to) {
        int[] lut = new int[LUT_STEPS + 1];
        for (int i = 0; i <= LUT_STEPS; i++) {
            lut[i] = lerpRGB(from, to, i / (float) LUT_STEPS);
        }
        return lut;
    }

    private static int lutIndex(float t) {
        int i = (int) (t * LUT_STEPS + 0.5f);
        return i < 0 ? 0 : (i > LUT_STEPS ? LUT_STEPS : i);
    }

    private static int lerpRGB(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) * (1f - t) + ((to >> 16) & 0xFF) * t);
        int g = (int) (((from >> 8) & 0xFF) * (1f - t) + ((to >> 8) & 0xFF) * t);
        int b = (int) ((from & 0xFF) * (1f - t) + (to & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        // Start the close animation; the actual screen close (super.onClose)
        // happens in render() once anim reaches 0.
        if (closing) return;
        closing = true;
        animStartNs = System.nanoTime();
        if (!ClickGuiModule.getInstance().animationsEnabled.getValue()) {
            anim = 0f;
            closing = false;
            super.onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {}

    public Category getSelectedCategory() { return selCategory; }
    public Module getSelectedModule() { return selModule; }
}

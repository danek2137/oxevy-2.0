package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.Oxevy;
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
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClickGuiScreen extends Screen {

    private static ClickGuiScreen INSTANCE;

    // ── Animation state ────────────────────────────────────────────
    private float anim = 1f;          // 0 = fully closed, 1 = fully open
    private boolean closing = false;  // true while the close animation plays
    private long animStartNs = 0;     // when the current anim phase started

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
    private Setting<String> focusedStringSetting = null;
    private Setting<Bind> listeningBindSetting = null;

    // ── Colors ─────────────────────────────────────────────────────
    private static final int C_BG       = 0xFF0A0A12;
    private static final int C_PANEL    = 0xFF12121E;
    private static final int C_CARD     = 0xFF1A1A28;
    private static final int C_CARD_HVR = 0xFF222234;
    private static final int C_INPUT    = 0xFF0E0E18;
    private static final int C_ACCENT   = 0xFF00FF66;
    private static final int C_ACCENT2  = 0xFF10B981;
    private static final int C_TXT1     = 0xFFE2E8F0;
    private static final int C_TXT2     = 0xFF94A3B8;
    private static final int C_TXT3     = 0xFF475569;
    private static final int C_SEP      = 0xFF1E1E2E;
    private static final int C_TOGGLE_OFF = 0xFF2F2F38;

    // ── Layout ─────────────────────────────────────────────────────
    private static final int GUI_W = 620;
    private static final int GUI_H = 420;
    private static final int TAB_H = 36;
    private static final int LEFT_W = 270;
    private static final int CARD_H = 32;
    private static final int CARD_GAP = 4;
    private static final int RIGHT_W = GUI_W - LEFT_W;

    private final Minecraft mc = Minecraft.getInstance();

    private ClickGuiScreen() {
        super(Component.literal("Oxevy GUI"));
    }

    // ── Singleton ──────────────────────────────────────────────────
    public static ClickGuiScreen getInstance() {
        if (INSTANCE == null || INSTANCE.mc == null) {
            INSTANCE = new ClickGuiScreen();
        }
        INSTANCE.closing = false;
        INSTANCE.anim = 0f;
        INSTANCE.animStartNs = System.nanoTime();
        return INSTANCE;
    }

    // ═══════════════════════════════════════════════════════════════
    //  RENDER
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        updateAnimation();

        // The close animation finished — actually leave the screen.
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

        ClickGuiModule cgm = ClickGuiModule.getInstance();
        boolean scaleAnim = cgm.scaleAnimation.getValue();
        boolean slideAnim = cgm.slideInAnimation.getValue();
        boolean animOn = cgm.animationsEnabled.getValue();

        var pose = ctx.pose();
        pose.pushPose();

        float eased = easeOutCubic(anim);
        if (animOn) {
            if (scaleAnim) {
                float scale = 0.9f + 0.1f * eased;
                pose.translate(guiX + GUI_W / 2f, guiY + GUI_H / 2f,0.0F);
                pose.scale(scale, scale,1.0F);
                pose.translate(-(guiX + GUI_W / 2f), -(guiY + GUI_H / 2f), 0.0F);
            }
            if (slideAnim) {
                // Slide in from the top of the screen; the close animation
                // reverses automatically (anim goes 1 -> 0) and slides back up.
                float slide = -(1f - eased) * (guiY + GUI_H);
                pose.translate(0f, slide,0.0F);
            }
        }

        // main bg
        RenderUtil.rect(ctx, guiX, guiY, guiX + GUI_W, guiY + GUI_H, C_BG);

        drawTabs(ctx, guiX, guiY, mx, my);
        drawModuleList(ctx, guiX, guiY + TAB_H, mx, my);
        drawSettingsPanel(ctx, guiX + LEFT_W, guiY + TAB_H, mx, my);
        drawFooter(ctx, guiX, guiY + GUI_H, mx, my);

        pose.popPose();

        super.render(ctx, mx, my, delta);
    }

    // ── Animation helpers ─────────────────────────────────────────
    private void updateAnimation() {
        ClickGuiModule cgm = ClickGuiModule.getInstance();
        if (!cgm.animationsEnabled.getValue()) {
            anim = closing ? 0f : 1f;
            return;
        }

        // Deterministic time-based animation: advances linearly toward the
        // target and completes in exactly `animDuration()` seconds. Exponential
        // decay was buggy — it never truly reaches the threshold, so the GUI
        // lingered almost-invisible for seconds after closing.
        float duration = animDuration();
        float elapsed = (System.nanoTime() - animStartNs) / 1e9f;
        float t = Math.min(elapsed / duration, 1f);

        if (closing) {
            anim = 1f - t;
        } else {
            anim = t;
        }
    }

    private float animDuration() {
        ClickGuiModule cgm = ClickGuiModule.getInstance();
        // animationSpeed (default 0.15) was a per-frame lerp factor; convert it
        // to a duration that stays snappy. Clamped so it can never drag on.
        float speed = Math.max(cgm.animationSpeed.getValue(), 0.01f);
        return Math.max(0.06f, Math.min(0.9f, 0.09f / speed));
    }

    private static float easeOutCubic(float t) {
        float p = 1f - t;
        return 1f - p * p * p;
    }

    // ── Tabs ───────────────────────────────────────────────────────
    private void drawTabs(GuiGraphics ctx, int gx, int gy, int mx, int my) {
        RenderUtil.rect(ctx, gx, gy, gx + GUI_W, gy + TAB_H, C_PANEL);

        int tx = gx + 8;
        for (Category cat : Category.values()) {
            int tw = CustomFont.font().width(cat.getName()) + 16;
            boolean hover = mx >= tx && mx < tx + tw && my >= gy && my < gy + TAB_H;
            boolean active = cat == selCategory;

            if (active) {
                RenderUtil.rect(ctx, tx, gy + TAB_H - 3, tx + tw, gy + TAB_H, C_ACCENT);
            } else if (hover) {
                RenderUtil.rect(ctx, tx, gy + TAB_H - 3, tx + tw, gy + TAB_H, 0x4000FF66);
            }

            int textColor = active ? C_ACCENT : (hover ? C_TXT1 : C_TXT2);
            ctx.drawString(CustomFont.font(), cat.getName(), tx + 8, gy + (TAB_H - 8) / 2, textColor);
            tx += tw + 4;
        }

        // search
        int sw = 130;
        int sx = gx + GUI_W - sw - 8;
        RenderUtil.rect(ctx, sx, gy + 6, sx + sw, gy + TAB_H - 6, C_INPUT);
        String prompt = searchText.isEmpty() && !searchFocused ? "Search..." : searchText;
        int promptColor = searchText.isEmpty() && !searchFocused ? C_TXT3 : C_TXT1;
        ctx.drawString(CustomFont.font(), prompt, sx + 6, gy + (TAB_H - 8) / 2, promptColor);

        if (searchFocused) {
            int cursorW = CustomFont.font().width(searchText);
            long blink = System.currentTimeMillis() / 500;
            if (blink % 2 == 0) {
                ctx.fill(sx + 6 + cursorW + 1, gy + 10, sx + 7 + cursorW + 1, gy + 10 + 10, C_ACCENT);
            }
        }
    }

    // ── Module List ────────────────────────────────────────────────
    private void drawModuleList(GuiGraphics ctx, int gx, int gy, int mx, int my) {
        int listTop = gy;
        int listBottom = gy + GUI_H - TAB_H;
        int listLeft = gx;
        int listRight = gx + LEFT_W;

        // panel bg
        RenderUtil.rect(ctx, listLeft, listTop, listRight, listBottom, C_PANEL);
        // separator
        RenderUtil.rect(ctx, listRight - 1, listTop, listRight, listBottom, C_SEP);

        List<Module> mods = getFilteredModules();
        ctx.enableScissor(listLeft, listTop, listRight, listBottom);

        int yOff = listTop + 4 - scrollOffset;
        int cardW = LEFT_W - 8;

        for (int i = 0; i < mods.size(); i++) {
            Module mod = mods.get(i);
            if (yOff + CARD_H < listTop) { yOff += CARD_H + CARD_GAP; continue; }
            if (yOff > listBottom) break;

            boolean hover = mx >= listLeft + 4 && mx < listLeft + 4 + cardW
                    && my >= yOff && my < yOff + CARD_H;
            boolean selected = mod == selModule;

            // card bg
            int cardBg = selected ? C_CARD_HVR : (hover ? 0xFF1E1E30 : C_CARD);
            RenderUtil.rect(ctx, listLeft + 4, yOff, listLeft + 4 + cardW, yOff + CARD_H, cardBg);

            // accent bar when enabled
            if (mod.isEnabled()) {
                ctx.fill(listLeft + 4, yOff, listLeft + 7, yOff + CARD_H, C_ACCENT);
            }

            // name
            int nameColor = mod.isEnabled() ? C_TXT1 : C_TXT2;
            ctx.drawString(CustomFont.font(), mod.getDisplayName(), listLeft + 14, yOff + (CARD_H - 8) / 2, nameColor);

            // bind badge
            Bind bind = mod.getBind();
            if (!bind.isEmpty()) {
                String bindStr = KeyboardUtil.getKeyName(bind.getKey());
                int bw = CustomFont.font().width(bindStr) + 8;
                int bx = listLeft + 4 + cardW - bw - 34;
                RenderUtil.rect(ctx, bx, yOff + 9, bx + bw, yOff + CARD_H - 9, C_BG);
                ctx.drawString(CustomFont.font(), bindStr, bx + 4, yOff + (CARD_H - 8) / 2, C_ACCENT2);
            }

            // toggle pill
            int px = listLeft + 4 + cardW - 30;
            int py = yOff + (CARD_H - 14) / 2;
            int pillBg = mod.isEnabled() ? C_ACCENT : C_TOGGLE_OFF;
            RenderUtil.rect(ctx, px, py, px + 26, py + 14, pillBg);
            int knobX = mod.isEnabled() ? px + 14 : px + 2;
            RenderUtil.rect(ctx, knobX, py + 2, knobX + 10, py + 12, C_TXT1);

            yOff += CARD_H + CARD_GAP;
        }

        maxScroll = Math.max(0, yOff - listBottom + scrollOffset);
        ctx.disableScissor();
    }

    // ── Settings Panel ─────────────────────────────────────────────
    private void drawSettingsPanel(GuiGraphics ctx, int px, int py, int mx, int my) {
        int pRight = px + RIGHT_W;
        int pBottom = py + GUI_H - TAB_H;

        RenderUtil.rect(ctx, px, py, pRight, pBottom, C_PANEL);

        if (selModule == null) {
            String hint = "Right-click a module to edit settings";
            ctx.drawString(CustomFont.font(), hint,
                    px + (RIGHT_W - CustomFont.font().width(hint)) / 2,
                    py + (GUI_H - TAB_H - 8) / 2,
                    C_TXT3);
            return;
        }

        // header
        ctx.drawString(CustomFont.font(), selModule.getDisplayName(), px + 12, py + 10, C_TXT1);
        RenderUtil.rect(ctx, px + 8, py + 24, pRight - 8, py + 25, C_SEP);

        // close hint
        String closeText = "[ESC]";
        ctx.drawString(CustomFont.font(), closeText, pRight - 8 - CustomFont.font().width(closeText), py + 10, C_TXT3);

        // settings list
        int listTop = py + 32;
        int listBottom = pBottom - 4;
        ctx.enableScissor(px, listTop, pRight, listBottom);

        List<Setting<?>> settings = new ArrayList<>();
        for (Setting<?> s : selModule.getSettings()) {
            if (!"Enabled".equals(s.getName()) && !"DisplayName".equals(s.getName()) && s.isVisible()) {
                settings.add(s);
            }
        }

        int sy = listTop + 2 - settingsScroll;
        int compW = RIGHT_W - 24;

        for (Setting<?> setting : settings) {
            SettingComponent comp = getOrCreateComponent(setting, px + 12, sy, compW);
            int h = comp.getHeight() + 4;
            if (sy + h < listTop) { sy += h; continue; }
            if (sy > listBottom) break;

            comp.render(ctx, mx, my, 0);
            sy += h;
        }

        settingsMaxScroll = Math.max(0, sy - listBottom + settingsScroll + 8);
        ctx.disableScissor();
    }

    // ── Footer ─────────────────────────────────────────────────────
    private void drawFooter(GuiGraphics ctx, int gx, int gyBottom, int mx, int my) {
        String footer = "LMB: Toggle | RMB: Settings | Scroll: Navigate | ESC: Close";
        int fy = gyBottom - 18;
        RenderUtil.rect(ctx, gx, fy, gx + GUI_W, gyBottom, C_PANEL);
        ctx.drawString(CustomFont.font(), footer,
                gx + (GUI_W - CustomFont.font().width(footer)) / 2,
                fy + 5, C_TXT3);
    }

    // ═══════════════════════════════════════════════════════════════
    //  INPUT
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int btn = button;
        int guiX = (mc.getWindow().getGuiScaledWidth() - GUI_W) / 2;
        int guiY = (mc.getWindow().getGuiScaledHeight() - GUI_H) / 2;

        // clear focus
        focusedStringSetting = null;
        listeningBindSetting = null;
        for (SettingComponent c : settingComponents.values()) {
            if (c instanceof BindComponent bc) bc.stopListening();
        }

        // tab clicks
        if (my >= guiY && my < guiY + TAB_H) {
            int tx = guiX + 8;
            for (Category cat : Category.values()) {
                int tw = CustomFont.font().width(cat.getName()) + 16;
                if (mx >= tx && mx < tx + tw) {
                    selCategory = cat;
                    selModule = null;
                    scrollOffset = 0;
                    settingsScroll = 0;
                    settingComponents.clear();
                    return true;
                }
                tx += tw + 4;
            }
            // search click
            int sw = 130;
            int sx = guiX + GUI_W - sw - 8;
            if (mx >= sx && mx < sx + sw && my >= guiY && my < guiY + TAB_H) {
                searchFocused = true;
                return true;
            }
            searchFocused = false;
            return true;
        }

        int listTop = guiY + TAB_H;
        int listBottom = guiY + GUI_H;
        int listLeft = guiX;
        int listRight = guiX + LEFT_W;

        // module list clicks
        if (mx >= listLeft && mx < listRight && my >= listTop && my < listBottom - 22) {
            searchFocused = false;
            List<Module> mods = getFilteredModules();
            int yOff = listTop + 4 - scrollOffset;
            int cardW = LEFT_W - 8;

            for (Module mod : mods) {
                if (my >= yOff && my < yOff + CARD_H && mx >= listLeft + 4 && mx < listLeft + 4 + cardW) {
                    int px = listLeft + 4 + cardW - 30;
                    if (btn == 0 && mx >= px && mx < px + 26) {
                        mod.toggle();
                        return true;
                    }
                    if (btn == 1) {
                        if (selModule == mod) {
                            selModule = null;
                            settingComponents.clear();
                        } else {
                            selModule = mod;
                            settingComponents.clear();
                            settingsScroll = 0;
                        }
                        return true;
                    }
                    if (btn == 0) {
                        mod.toggle();
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
        if (mx >= settingsLeft && mx < settingsRight && my >= listTop && my < listBottom - 22) {
            if (selModule == null) return true;

            // check close button
            String closeText = "[ESC]";
            int closeX = settingsRight - 8 - CustomFont.font().width(closeText);
            if (mx >= closeX && mx < closeX + CustomFont.font().width(closeText) && my >= guiY + 8 && my < guiY + 22) {
                selModule = null;
                settingComponents.clear();
                return true;
            }

            int compTop = listTop + 32;
            int compBottom = listBottom - 22;
            int compW = RIGHT_W - 24;
            int sy = compTop + 2 - settingsScroll;

            for (Setting<?> setting : selModule.getSettings()) {
                if ("Enabled".equals(setting.getName()) || "DisplayName".equals(setting.getName()) || !setting.isVisible()) continue;
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

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (SettingComponent comp : settingComponents.values()) {
            comp.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int guiX = (mc.getWindow().getGuiScaledWidth() - GUI_W) / 2;
        int guiY = (mc.getWindow().getGuiScaledHeight() - GUI_H) / 2;

        // settings panel scroll
        if (mx >= guiX + LEFT_W && mx < guiX + GUI_W && my >= guiY + TAB_H && my < guiY + GUI_H - 22) {
            settingsScroll = (int) Math.max(0, settingsScroll - vertical * 16);
            settingsScroll = Math.min(settingsScroll, settingsMaxScroll);
            return true;
        }
        // module list scroll
        if (mx >= guiX && mx < guiX + LEFT_W && my >= guiY + TAB_H && my < guiY + GUI_H - 22) {
            scrollOffset = (int) Math.max(0, scrollOffset - vertical * 16);
            scrollOffset = Math.min(scrollOffset, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;

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
                selModule = null;
                settingComponents.clear();
            } else {
                onClose();
            }
            return true;
        }

        for (SettingComponent c : settingComponents.values()) {
            if (c.isCapturingInput() && c.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        String value = String.valueOf(chr);

        if (focusedStringSetting != null) {
            String cur = focusedStringSetting.getValue();
            if (cur.length() < 64) focusedStringSetting.setValue(cur + value);
            return true;
        }
        if (searchFocused) {
            if (searchText.length() < 64) { searchText += value; scrollOffset = 0; }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private List<Module> getFilteredModules() {
        if (searchText.isBlank()) {
            List<Module> list = new ArrayList<>();
            for (Module m : Oxevy.moduleManager.getModules()) {
                if (m.getCategory() == selCategory && !m.hidden) list.add(m);
            }
            return list;
        }
        String lower = searchText.toLowerCase();
        List<Module> list = new ArrayList<>();
        for (Module m : Oxevy.moduleManager.getModules()) {
            if (!m.hidden && m.getDisplayName().toLowerCase().contains(lower)) list.add(m);
        }
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
        if (val instanceof Boolean)   return new BooleanComponent((Setting<Boolean>) setting, x, y, w, h);
        if (val instanceof Number) {
            if (setting.getMin() != null) return new SliderComponent((Setting<Number>) setting, x, y, w, h);
            return new StringSettingComponent(setting, x, y, w, h);
        }
        if (val instanceof Bind)      return new BindComponent((Setting<Bind>) setting, x, y, w, h);
        if (val instanceof Color)     return new ColorComponent((Setting<Color>) setting, x, y, w, h);
        if (setting.isEnumSetting())    return new EnumComponent(setting, x, y, w, h);
        if (setting.isStringSetting())  return new StringComponent((Setting<String>) setting, x, y, w, h);
        if (setting.isButtonSetting())  return new ButtonComponent((Setting<Runnable>) setting, x, y, w, h);
        return new StringSettingComponent(setting, x, y, w, h);
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

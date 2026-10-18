package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.KeyboardUtil;
import me.alpha432.oxevy.util.render.AnimationUtil;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeystrokesHudModule extends HudModule {
    public final Setting<Float> keySize = num("KeySize", 20f, 12f, 30f);
    public final Setting<Integer> padding = num("Padding", 2, 0, 8);
    public final Setting<Float> animationSpeed = num("AnimationSpeed", 0.15f, 0.01f, 0.5f);
    public final Setting<String> customKeys = str("Custom Keys", "");

    // ── Movement / mouse keys ────────────────────────────────────
    public final Setting<Boolean> showW = bool("W", true);
    public final Setting<Boolean> showA = bool("A", true);
    public final Setting<Boolean> showS = bool("S", true);
    public final Setting<Boolean> showD = bool("D", true);
    public final Setting<Boolean> showSpace = bool("Space", true);
    public final Setting<Boolean> showSprint = bool("Sprint", false);
    public final Setting<Boolean> showSneak = bool("Sneak", false);
    public final Setting<Boolean> showLMB = bool("LMB", true);
    public final Setting<Boolean> showMMB = bool("MMB", false);
    public final Setting<Boolean> showRMB = bool("RMB", true);

    // ── Extra keys (off by default, toggle to show) ──────────────
    public final Setting<Boolean> showQ = bool("Q", false);
    public final Setting<Boolean> showE = bool("E", false);
    public final Setting<Boolean> showR = bool("R", false);
    public final Setting<Boolean> showF = bool("F", false);
    public final Setting<Boolean> showT = bool("T", false);
    public final Setting<Boolean> showC = bool("C", false);
    public final Setting<Boolean> showZ = bool("Z", false);
    public final Setting<Boolean> showX = bool("X", false);
    public final Setting<Boolean> showV = bool("V", false);
    public final Setting<Boolean> showTab = bool("Tab", false);
    public final Setting<Boolean> showF5 = bool("F5", false);
    public final Setting<Boolean> showM = bool("M", false);

    private static final int GRID_COLS = 3;

    private record KeyDef(String label, int key, boolean mouse, int row, int col, int span) {}

    private final Map<Integer, Float> keyAnimations = new HashMap<>();

    // Cached parse of customKeys
    private String parsedCustomKeys = null;
    private final List<CustomKey> customKeyDefs = new ArrayList<>();

    private record CustomKey(String label, int key) {}

    public KeystrokesHudModule() {
        super("Keystrokes", "Shows pressed keys with configurable buttons", 60, 60);
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (nullCheck()) return;

        refreshCustomKeys();

        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();

        float size = keySize.getValue();
        int pad = padding.getValue();
        float cellW = size;
        float cellH = size;

        List<KeyDef> keys = buildVisibleKeys();
        if (keys.isEmpty()) {
            String none = "Keys: Off";
            int tw = mc.font.width(none);
            int th = mc.font.lineHeight;
            ctx.drawString(mc.font, none, (int) x, (int) y, 0xFF94A3B8);
            setWidth(tw + 8);
            setHeight(th + 6);
            return;
        }

        int rows = 0;
        for (KeyDef k : keys) {
            rows = Math.max(rows, k.row + 1);
        }

        float gridW = GRID_COLS * cellW + (GRID_COLS - 1) * pad;
        float gridH = rows * cellH + (rows - 1) * pad;
        setWidth(gridW);
        setHeight(gridH);

        for (KeyDef def : keys) {
            float bw = def.span * cellW + (def.span - 1) * pad;
            float bx = x + def.col * (cellW + pad);
            float by = y + def.row * (cellH + pad);
            drawKey(ctx, bx, by, bw, cellH, def);
        }
    }

    private void refreshCustomKeys() {
        String raw = customKeys.getValue();
        if (raw != null && raw.equals(parsedCustomKeys)) return;
        parsedCustomKeys = raw;
        customKeyDefs.clear();

        if (raw == null || raw.isBlank()) return;

        for (String token : raw.split("[,\\s]+")) {
            if (token.isEmpty()) continue;
            int key = keyFromName(token);
            if (key < 0) continue;
            customKeyDefs.add(new CustomKey(token.toUpperCase(), key));
        }
    }

    private int maxPresetRow() {
        int max = 0;
        if (showA.getValue() || showS.getValue() || showD.getValue()) max = Math.max(max, 1);
        if (showSpace.getValue()) max = Math.max(max, 2);
        if (showSprint.getValue() || showLMB.getValue() || showRMB.getValue()) max = Math.max(max, 3);
        if (showSneak.getValue() || showMMB.getValue()) max = Math.max(max, 4);
        return max;
    }

    private List<KeyDef> buildVisibleKeys() {
        List<KeyDef> list = new ArrayList<>();
        add(list, "W", GLFW.GLFW_KEY_W, false, 0, 1, 1, showW.getValue());
        add(list, "A", GLFW.GLFW_KEY_A, false, 1, 0, 1, showA.getValue());
        add(list, "S", GLFW.GLFW_KEY_S, false, 1, 1, 1, showS.getValue());
        add(list, "D", GLFW.GLFW_KEY_D, false, 1, 2, 1, showD.getValue());
        add(list, "SPACE", GLFW.GLFW_KEY_SPACE, false, 2, 0, 3, showSpace.getValue());
        add(list, "CTL", GLFW.GLFW_KEY_LEFT_CONTROL, false, 3, 0, 1, showSprint.getValue());
        add(list, "LMB", GLFW.GLFW_MOUSE_BUTTON_LEFT, true, 3, 1, 1, showLMB.getValue());
        add(list, "RMB", GLFW.GLFW_MOUSE_BUTTON_RIGHT, true, 3, 2, 1, showRMB.getValue());
        add(list, "SFT", GLFW.GLFW_KEY_LEFT_SHIFT, false, 4, 0, 1, showSneak.getValue());
        add(list, "MMB", GLFW.GLFW_MOUSE_BUTTON_MIDDLE, true, 4, 1, 1, showMMB.getValue());

        int flowRow = maxPresetRow() + 1;
        KeyDef[] extraDefs = new KeyDef[]{
                new KeyDef("Q", GLFW.GLFW_KEY_Q, false, 0, 0, 1),
                new KeyDef("E", GLFW.GLFW_KEY_E, false, 0, 0, 1),
                new KeyDef("R", GLFW.GLFW_KEY_R, false, 0, 0, 1),
                new KeyDef("F", GLFW.GLFW_KEY_F, false, 0, 0, 1),
                new KeyDef("T", GLFW.GLFW_KEY_T, false, 0, 0, 1),
                new KeyDef("C", GLFW.GLFW_KEY_C, false, 0, 0, 1),
                new KeyDef("Z", GLFW.GLFW_KEY_Z, false, 0, 0, 1),
                new KeyDef("X", GLFW.GLFW_KEY_X, false, 0, 0, 1),
                new KeyDef("V", GLFW.GLFW_KEY_V, false, 0, 0, 1),
                new KeyDef("TAB", GLFW.GLFW_KEY_TAB, false, 0, 0, 1),
                new KeyDef("F5", GLFW.GLFW_KEY_F5, false, 0, 0, 1),
                new KeyDef("M", GLFW.GLFW_KEY_M, false, 0, 0, 1),
        };
        boolean[] extras = new boolean[]{
                showQ.getValue(), showE.getValue(), showR.getValue(), showF.getValue(),
                showT.getValue(), showC.getValue(), showZ.getValue(), showX.getValue(),
                showV.getValue(), showTab.getValue(), showF5.getValue(), showM.getValue(),
        };
        int idx = 0;
        for (int i = 0; i < extraDefs.length; i++) {
            if (!extras[i]) continue;
            int row = flowRow + idx / GRID_COLS;
            int c = idx % GRID_COLS;
            list.add(new KeyDef(extraDefs[i].label(), extraDefs[i].key(), false, row, c, 1));
            idx++;
        }

        for (CustomKey ck : customKeyDefs) {
            int row = flowRow + idx / GRID_COLS;
            int c = idx % GRID_COLS;
            list.add(new KeyDef(ck.label(), ck.key(), false, row, c, 1));
            idx++;
        }
        return list;
    }

    private void add(List<KeyDef> list, String label, int key, boolean mouse, int row, int col, int span, boolean shown) {
        if (shown) list.add(new KeyDef(label, key, mouse, row, col, span));
    }

    private void drawKey(GuiGraphics ctx, float x, float y, float w, float h, KeyDef def) {
        boolean pressed = isPressed(def);
        float target = pressed ? 1.0f : 0.0f;
        int animKey = def.mouse() ? 1000 + def.key() : def.row() * 100 + def.col();
        keyAnimations.putIfAbsent(animKey, 0.0f);
        float anim = AnimationUtil.animate(keyAnimations.get(animKey), target, animationSpeed.getValue());
        keyAnimations.put(animKey, anim);

        int pressColor = ClickGuiModule.getInstance().color.getValue().getRGB();
        int color = AnimationUtil.interpolateColor(0xFF11111B, pressColor, anim);

        if (rounded.getValue()) {
            RenderUtil.roundRect(ctx, x, y, w, h, radius.getValue(), color);
        } else {
            RenderUtil.rect(ctx, x, y, x + w, y + h, color);
        }

        int textColor = AnimationUtil.interpolateColor(0xFFAAAAAA, 0xFFFFFFFF, anim);
        int tw = mc.font.width(def.label());
        int th = mc.font.lineHeight;
        ctx.drawString(mc.font, def.label(), (int) (x + (w - tw) / 2f), (int) (y + (h - th) / 2f), textColor);
    }

    private boolean isPressed(KeyDef def) {
        if (def.mouse()) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), def.key()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(mc.getWindow().handle(), def.key()) == GLFW.GLFW_PRESS;
    }

    private static int keyFromName(String name) {
        for (int i = 0; i <= GLFW.GLFW_KEY_LAST; i++) {
            if (KeyboardUtil.getKeyName(i).equalsIgnoreCase(name)) return i;
        }
        // allow raw numeric keycodes
        try {
            int v = Integer.parseInt(name);
            if (v > 0) return v;
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }
}
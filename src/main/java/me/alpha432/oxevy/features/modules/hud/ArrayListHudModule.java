package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.AnimationUtil;
import me.alpha432.oxevy.util.ColorUtil;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class ArrayListHudModule extends HudModule {
    public final Setting<Boolean> sortAlphabetical = bool("SortAlphabetical", false);
    public final Setting<Boolean> rainbow = bool("Rainbow", false);
    public final Setting<Boolean> smoothAnimations = bool("SmoothAnimations", true);
    public final Setting<Float> animationSpeed = num("AnimationSpeed", 0.15f, 0.05f, 0.5f);
    public final Setting<Boolean> bar = bool("Bar", true);
    public final Setting<Boolean> icons = bool("Icons", true);
    public final Setting<Boolean> shadow = bool("Shadow", true);
    public final Setting<Integer> spacing = num("Spacing", 3, 0, 8);
    public final Setting<Integer> textOffset = num("TextOffset", 4, 0, 10);

    private final Map<Module, Float> moduleAnimations = new HashMap<>();
    private final List<Module> enabledModules = new ArrayList<>(128);
    private final Map<Module, Integer> cachedNameWidths = new HashMap<>(128);
    private final Set<Module> enabledSet = new HashSet<>(128);

    private static final String[] CATEGORY_ICONS = {"⚔", "◈", "◉", "◐", "⚙", "⚡", "▤"};

    public ArrayListHudModule() {
        super("ArrayList", "Shows enabled modules", 100, 50);
        pos.setValue(new org.joml.Vector2f(0.01f, 0.11f));
    }

    @Override
    public void drawContent(Render2DEvent e) {
        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();
        int lineHeight = mc.font.lineHeight;
        float drawY = y;
        float maxWidth = 0;

        enabledModules.clear();
        enabledSet.clear();
        cachedNameWidths.clear();

        for (Module module : Oxevy.moduleManager.getModules()) {
            if ((module.isEnabled() || (smoothAnimations.getValue() && moduleAnimations.getOrDefault(module, 0f) > 0.01f))
                && !module.hidden && module.getCategory() != Module.Category.HUD) {
                enabledModules.add(module);
                if (module.isEnabled()) enabledSet.add(module);
            }
        }

        for (Module module : enabledModules) {
            String displayText = icons.getValue() ? getCategoryIcon(module.getCategory()) + " " + module.getName() : module.getName();
            cachedNameWidths.put(module, mc.font.width(displayText));
        }

        if (sortAlphabetical.getValue()) {
            enabledModules.sort(Comparator.comparing(Module::getName));
        } else {
            enabledModules.sort((m1, m2) -> Integer.compare(
                cachedNameWidths.getOrDefault(m2, 0),
                cachedNameWidths.getOrDefault(m1, 0)
            ));
        }

        for (Module module : enabledModules) {
            maxWidth = Math.max(maxWidth, cachedNameWidths.getOrDefault(module, 0));
        }

        int iconOffset = icons.getValue() ? mc.font.width("⚔ ") : 0;
        setWidth(maxWidth + (bar.getValue() ? 12 : 8) + iconOffset);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        boolean isLeft = x < screenWidth / 2.0f;

        final boolean doRainbow = rainbow.getValue();
        final boolean doSmooth = smoothAnimations.getValue();
        final float speed = animationSpeed.getValue();
        final boolean showIcons = icons.getValue();
        final boolean useShadow = shadow.getValue();
        final int customSpacing = spacing.getValue();
        final int offset = textOffset.getValue();

        final ClickGuiModule clickGui = ClickGuiModule.getInstance();
        final int baseColor = clickGui != null && clickGui.color.getValue() != null
            ? clickGui.color.getValue().getRGB()
            : 0xFF0084FF;

        for (int i = 0; i < enabledModules.size(); i++) {
            Module module = enabledModules.get(i);
            String text = showIcons ? getCategoryIcon(module.getCategory()) + " " + module.getName() : module.getName();
            int textWidth = cachedNameWidths.getOrDefault(module, mc.font.width(text));

            float targetAnim = enabledSet.contains(module) ? 1.0f : 0.0f;
            moduleAnimations.putIfAbsent(module, 0.0f);
            float currentAnim = moduleAnimations.get(module);
            currentAnim = AnimationUtil.animate(currentAnim, targetAnim, speed, AnimationUtil.Easing.EASE_OUT);
            moduleAnimations.put(module, currentAnim);

            if (currentAnim < 0.01f && !enabledSet.contains(module)) continue;

            int color;
            if (doRainbow) {
                float hue = ((System.currentTimeMillis() + (i * 100)) % 3000) / 3000f;
                color = Color.getHSBColor(hue, 0.8f, 1.0f).getRGB();
            } else {
                color = baseColor;
            }

            float slide = doSmooth ? (1.0f - currentAnim) * (isLeft ? -textWidth - 20 : textWidth + 20) : 0;
            float alpha = currentAnim;

            int textX;
            int barStart, barEnd;

            if (isLeft) {
                textX = (int) (x + offset + slide);
                barStart = (int) (x + slide);
                barEnd = (int) (x + 3 + slide);
            } else {
                textX = (int) (x + getWidth() - textWidth - offset + slide);
                barStart = (int) (x + getWidth() - 3 + slide);
                barEnd = (int) (x + getWidth() + slide);
            }

            if (bar.getValue()) {
                RenderUtil.rect(ctx, barStart, drawY, barEnd, drawY + lineHeight + customSpacing, color);
            }

            if (showIcons && isLeft) {
                String icon = getCategoryIcon(module.getCategory());
                ctx.drawString(mc.font, icon, textX, (int) (drawY + customSpacing / 2f), color, useShadow);
                textX += mc.font.width(icon + " ");
            }

            int textColor = AnimationUtil.interpolateColor(ColorUtil.toRGBA(0, 0, 0, 0), 0xFFFFFFFF, currentAnim);
            ctx.drawString(mc.font, text, textX, (int) (drawY + customSpacing / 2f), textColor, useShadow);

            drawY += (lineHeight + customSpacing) * currentAnim;
        }

        moduleAnimations.entrySet().removeIf(entry -> !enabledSet.contains(entry.getKey()) && entry.getValue() < 0.01f);
    }

    private String getCategoryIcon(Module.Category category) {
        if (category == null || category.ordinal() >= CATEGORY_ICONS.length) {
            return "•";
        }
        return CATEGORY_ICONS[category.ordinal()];
    }
}
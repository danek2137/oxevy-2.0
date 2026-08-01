package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Bind;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import me.alpha432.oxevy.util.KeyboardUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KeybindsHudModule extends HudModule {
    public final Setting<Boolean> sortAlphabetical = bool("SortAlphabetical", false);
    public final Setting<Boolean> rainbow = bool("Rainbow", false);
    public final Setting<Boolean> bar = bool("Bar", true);
    public final Setting<Boolean> shadow = bool("Shadow", true);
    public final Setting<Integer> spacing = num("Spacing", 3, 0, 8);
    public final Setting<Integer> textOffset = num("TextOffset", 4, 0, 10);

    public KeybindsHudModule() {
        super("Keybinds", "Displays modules with keybinds", 100, 50);
    }

    @Override
    public void drawContent(Render2DEvent e) {
        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();
        int lineHeight = mc.font.lineHeight;
        float drawY = y;
        float maxWidth = 0;

        List<ModuleEntry> entries = new ArrayList<>();

        for (Module module : Oxevy.moduleManager.getModules()) {
            Bind bind = module.getBind();
            if (!bind.isEmpty() && module.getCategory() != Module.Category.HUD) {
                String keyName = KeyboardUtil.getKeyName(bind.getKey());
                String text = module.getName() + " §7[§f" + keyName + "§7]";
                int width = mc.font.width(text);
                entries.add(new ModuleEntry(module, text, width));
                maxWidth = Math.max(maxWidth, width);
            }
        }

        if (sortAlphabetical.getValue()) {
            entries.sort(Comparator.comparing(entry -> entry.module.getName()));
        } else {
            entries.sort(Comparator.comparingInt(entry -> -entry.width));
        }

        setWidth(maxWidth + (bar.getValue() ? 12 : 8));
        setHeight(entries.size() * (lineHeight + spacing.getValue()));

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        boolean isLeft = x < screenWidth / 2.0f;

        final boolean doRainbow = rainbow.getValue();
        final boolean useShadow = shadow.getValue();
        final int customSpacing = spacing.getValue();
        final int offset = textOffset.getValue();

        final ClickGuiModule clickGui = ClickGuiModule.getInstance();
        final int baseColor = clickGui != null && clickGui.color.getValue() != null
            ? clickGui.color.getValue().getRGB()
            : 0xFF0084FF;

        for (int i = 0; i < entries.size(); i++) {
            ModuleEntry entry = entries.get(i);
            int color;
            if (doRainbow) {
                float hue = ((System.currentTimeMillis() + (i * 100)) % 3000) / 3000f;
                color = Color.getHSBColor(hue, 0.8f, 1.0f).getRGB();
            } else {
                color = baseColor;
            }

            int textX;
            int barStart, barEnd;

            if (isLeft) {
                textX = (int) (x + offset);
                barStart = (int) (x);
                barEnd = (int) (x + 3);
            } else {
                textX = (int) (x + getWidth() - entry.width - offset);
                barStart = (int) (x + getWidth() - 3);
                barEnd = (int) (x + getWidth());
            }

            if (bar.getValue()) {
                RenderUtil.rect(ctx, barStart, drawY, barEnd, drawY + lineHeight + customSpacing, color);
            }

            ctx.drawString(mc.font, entry.text, textX, (int) (drawY + customSpacing / 2f), 0xFFFFFFFF, useShadow);

            drawY += lineHeight + customSpacing;
        }
    }

    private static class ModuleEntry {
        final Module module;
        final String text;
        final int width;

        ModuleEntry(Module module, String text, int width) {
            this.module = module;
            this.text = text;
            this.width = width;
        }
    }
}

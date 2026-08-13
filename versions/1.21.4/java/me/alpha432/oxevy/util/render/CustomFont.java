package me.alpha432.oxevy.util.render;

import me.alpha432.oxevy.mixin.accessor.FontManagerAccessor;
import me.alpha432.oxevy.mixin.accessor.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

public class CustomFont {
    private static final ResourceLocation OXEVY_FONT = ResourceLocation.fromNamespaceAndPath("oxevy", "oxevy");
    private static final ResourceLocation LOGO_FONT = ResourceLocation.fromNamespaceAndPath("oxevy", "logo");

    private static Font oxevyFont;
    private static Font logoFont;

    public static void init() {
        if (((MinecraftAccessor) Minecraft.getInstance()).getFontManager() != null) {
            oxevyFont = build(OXEVY_FONT);
            logoFont = build(LOGO_FONT);
        }
    }

    private static Font build(ResourceLocation id) {
        return new Font(rl -> {
            FontManagerAccessor accessor = (FontManagerAccessor) ((MinecraftAccessor) Minecraft.getInstance()).getFontManager();
            return accessor.invokeGetFontSetRaw(id);
        }, false);
    }

    public static Font font() {
        if (oxevyFont == null) init();
        return oxevyFont;
    }

    public static Font logo() {
        if (logoFont == null) init();
        return logoFont;
    }
}
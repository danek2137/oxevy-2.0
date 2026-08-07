package me.alpha432.oxevy.util.render;

import me.alpha432.oxevy.mixin.accessor.FontManagerAccessor;
import me.alpha432.oxevy.mixin.accessor.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class CustomFont {
    private static final Identifier OXEVY_FONT = Identifier.fromNamespaceAndPath("oxevy", "oxevy");
    private static final Identifier LOGO_FONT = Identifier.fromNamespaceAndPath("oxevy", "logo");
    private static Font cached;
    private static Font cachedLogo;

    private CustomFont() {
        throw new AssertionError("Can't create an instance of utility class");
    }

    public static Font font() {
        Font font = cached;
        if (font == null) {
            font = cached = build(OXEVY_FONT);
        }
        return font;
    }

    public static Font logo() {
        Font font = cachedLogo;
        if (font == null) {
            font = cachedLogo = build(LOGO_FONT);
        }
        return font;
    }

    private static Font build(Identifier id) {
        FontManager manager = ((MinecraftAccessor) Minecraft.getInstance()).getFontManager();
        Font.Provider provider = new Font.Provider() {
            @Override
            public GlyphSource glyphs(FontDescription description) {
                return fontSet(manager, id).source(false);
            }

            @Override
            public EffectGlyph effect() {
                return fontSet(manager, id).whiteGlyph();
            }
        };
        return new Font(provider);
    }

    private static FontSet fontSet(FontManager manager, Identifier id) {
        return ((FontManagerAccessor) manager).invokeGetFontSetRaw(id);
    }
}

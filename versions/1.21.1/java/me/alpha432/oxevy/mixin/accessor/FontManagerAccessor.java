package me.alpha432.oxevy.mixin.accessor;

import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FontManager.class)
public interface FontManagerAccessor {
    @Invoker("getFontSetRaw")
    FontSet invokeGetFontSetRaw(ResourceLocation id);

    @Invoker("getFontSetCached")
    FontSet invokeGetFontSetCached(ResourceLocation id);
}

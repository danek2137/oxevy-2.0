package me.alpha432.oxevy.mixin.accessor;

import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FontManager.class)
public interface FontManagerAccessor {
    @Invoker("getFontSetRaw")
    FontSet invokeGetFontSetRaw(Identifier id);
}

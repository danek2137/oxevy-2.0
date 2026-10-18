package me.alpha432.oxevy.mixin.accessor;

import com.mojang.blaze3d.opengl.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlTexture.class)
public interface GlTextureAccessor {
    @Accessor("firstFboId")
    int getFirstFboId();
}

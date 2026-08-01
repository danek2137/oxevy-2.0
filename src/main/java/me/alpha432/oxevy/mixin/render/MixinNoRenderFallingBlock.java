package me.alpha432.oxevy.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoRenderModule;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FallingBlockRenderer.class)
public class MixinNoRenderFallingBlock {
    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private boolean modifyShouldRender(boolean original, FallingBlockEntity entity) {
        NoRenderModule noRender = Oxevy.moduleManager.getModuleByClass(NoRenderModule.class);
        if (noRender.noFallingBlocks()) return false;
        return original;
    }
}

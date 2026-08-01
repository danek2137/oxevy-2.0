package me.alpha432.oxevy.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoRenderModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ItemRenderer.class)
public class MixinNoRenderFoil {
    @ModifyReturnValue(method = "getFoilRenderTypes", at = @At("RETURN"))
    private static List<RenderType> modifyFoilRenderTypes(List<RenderType> original, RenderType layer, boolean solid, boolean glint) {
        NoRenderModule noRender = Oxevy.moduleManager.getModuleByClass(NoRenderModule.class);
        if (noRender.noEnchantGlint()) return List.of();
        return original;
    }
}

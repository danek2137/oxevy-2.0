package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NoRenderModule;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class MixinNoRenderFoil {
    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
    private void onHasFoil(CallbackInfoReturnable<Boolean> cir) {
        NoRenderModule noRender = Oxevy.moduleManager.getModuleByClass(NoRenderModule.class);
        if (noRender != null && noRender.noEnchantGlint()) {
            cir.setReturnValue(false);
        }
    }
}

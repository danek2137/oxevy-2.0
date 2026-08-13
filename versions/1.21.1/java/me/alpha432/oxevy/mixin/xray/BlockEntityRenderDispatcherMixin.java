package me.alpha432.oxevy.mixin.xray;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.XRayModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true)
    private <E extends BlockEntity> void onRender(
        E entity, float tickProgress, PoseStack matrices, MultiBufferSource bufferSource, CallbackInfo ci)
    {
        if (Oxevy.moduleManager == null) return;
        XRayModule xray = Oxevy.moduleManager.getModuleByClass(XRayModule.class);
        if (xray != null && xray.shouldHideBlockEntity(entity))
            ci.cancel();
    }
}

package me.alpha432.oxevy.mixin.xray;

import com.mojang.blaze3d.vertex.VertexConsumer;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.XRayModule;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LiquidBlockRenderer.class)
public class FluidRendererMixin {
    @Unique
    private static final ThreadLocal<Float> currentOpacity =
        ThreadLocal.withInitial(() -> 1F);

    @Inject(
        method = "tesselate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void onTesselate(BlockAndTintGetter level, BlockPos pos,
        VertexConsumer consumer, BlockState blockState, FluidState fluidState,
        CallbackInfo ci)
    {
        if (Oxevy.moduleManager == null) { currentOpacity.set(1F); return; }
        XRayModule xray = Oxevy.moduleManager.getModuleByClass(XRayModule.class);
        if (xray == null || !xray.isEnabled())
        {
            currentOpacity.set(1F);
            return;
        }

        if (xray.isVisible(blockState.getBlock(), pos))
        {
            currentOpacity.set(1F);
        } else if (xray.isOpacityMode())
        {
            currentOpacity.set(xray.getOpacityFloat());
        } else
        {
            ci.cancel();
        }
    }

    @Inject(
        method = "isFaceOccludedByNeighbor(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;FLnet/minecraft/world/level/block/state/BlockState;)Z",
        at = @At("RETURN"),
        cancellable = true)
    private static void onIsFaceOccludedByNeighbor(BlockGetter getter,
        BlockPos pos, Direction direction, float height,
        BlockState neighborState, CallbackInfoReturnable<Boolean> cir)
    {
        if (Oxevy.moduleManager == null) return;
        XRayModule xray = Oxevy.moduleManager.getModuleByClass(XRayModule.class);
        if (xray == null || !xray.isEnabled()) return;

        if (cir.getReturnValueZ()) return;

        if (xray.isOpacityMode()) return;

        if (!xray.isVisible(neighborState.getBlock(), pos))
            cir.setReturnValue(true);
    }

    @ModifyArg(
        method = "vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFI)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
        index = 3)
    private float modifyFluidAlpha(float alpha)
    {
        float opacity = currentOpacity.get();
        return Math.min(alpha, opacity);
    }
}

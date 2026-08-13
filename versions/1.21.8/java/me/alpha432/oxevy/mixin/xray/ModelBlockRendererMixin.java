package me.alpha432.oxevy.mixin.xray;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.XRayModule;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
    @Unique
    private static ThreadLocal<Float> currentOpacity =
        ThreadLocal.withInitial(() -> 1F);

    @WrapOperation(
        method = "shouldRenderFace(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;ZLnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private static boolean onShouldRenderFace(BlockState neighborState,
        BlockState state, Direction side, Operation<Boolean> original,
        BlockAndTintGetter world, BlockState sourceState,
        boolean checkNeighbor, Direction direction, BlockPos neighborPos)
    {
        if (Oxevy.moduleManager == null) return original.call(neighborState, state, side);
        XRayModule xray = Oxevy.moduleManager.getModuleByClass(XRayModule.class);
        if (xray == null || !xray.isEnabled())
            return original.call(neighborState, state, side);

        BlockPos pos = neighborPos.relative(direction.getOpposite());
        Boolean shouldDrawSide = xray.shouldDrawSide(state, pos);

        if (!xray.isOpacityMode() || xray.isVisible(state.getBlock(), pos))
            currentOpacity.set(1F);
        else
            currentOpacity.set(xray.getOpacityFloat());

        if (shouldDrawSide != null)
            return shouldDrawSide;

        return original.call(neighborState, state, side);
    }

    @ModifyArg(
        method = "putQuadData(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;Lnet/minecraft/client/renderer/block/ModelBlockRenderer$CommonRenderStorage;I)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"),
        index = 6)
    private float modifyOpacityAlpha(float alpha)
    {
        float opacity = currentOpacity.get();
        return alpha * opacity;
    }
}

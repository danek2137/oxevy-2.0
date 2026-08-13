package me.alpha432.oxevy.mixin.xray;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.XRayModule;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
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
        method = {
            "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JI)V",
            "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JI)V"
        },
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean onShouldRenderFace(BlockState state,
        BlockGetter getter, BlockPos pos, Direction side,
        BlockPos neighborPos, Operation<Boolean> original,
        BlockAndTintGetter world, BakedModel bakedModel,
        BlockState sourceState, BlockPos sourcePos)
    {
        if (Oxevy.moduleManager == null)
            return original.call(state, getter, pos, side, neighborPos);

        XRayModule xray = Oxevy.moduleManager.getModuleByClass(XRayModule.class);
        if (xray == null || !xray.isEnabled())
            return original.call(state, getter, pos, side, neighborPos);

        Boolean shouldDrawSide = xray.shouldDrawSide(state, pos);

        if (!xray.isOpacityMode() || xray.isVisible(state.getBlock(), pos))
            currentOpacity.set(1F);
        else
            currentOpacity.set(xray.getOpacityFloat());

        if (shouldDrawSide != null)
            return shouldDrawSide;

        return original.call(state, getter, pos, side, neighborPos);
    }

    @ModifyArg(
        method = "putQuadData",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"),
        index = 6)
    private float modifyOpacityAlpha(float alpha)
    {
        float opacity = currentOpacity.get();
        return alpha * opacity;
    }
}

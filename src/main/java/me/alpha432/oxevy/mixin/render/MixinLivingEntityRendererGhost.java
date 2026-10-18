package me.alpha432.oxevy.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oxevy.util.entity.GhostPlayerEntity;
import me.alpha432.oxevy.util.interfaces.GhostRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Renders Blink's ghosts translucent and hides their nametag.
 *
 * <p>The tint passed to {@code SubmitNodeCollector.submitModel} is the vertex
 * colour and it carries an alpha component: vanilla itself draws an invisible
 * entity with {@code 0x26FFFFFF}. The player model already submits through
 * {@code RenderTypes.entityTranslucent}, so alpha blending is already enabled
 * and the only thing missing for a ghost is that tint.
 *
 * <p>Because the ghost alpha is animated by BlinkModule on the game thread
 * while submission happens on the render thread, the entity reference carried
 * by the render state is re-read here instead of being cached in the state -
 * that way the fade is always current.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererGhost {

    @WrapOperation(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V")
    )
    private void oxevy$ghostModelTint(SubmitNodeCollector collector, Model<?> model, Object state,
            PoseStack poseStack, RenderType renderType, int light, int overlay, int color,
            TextureAtlasSprite sprite, int outlineColor,
            ModelFeatureRenderer.CrumblingOverlay crumbling, Operation<Void> original) {

        Entity entity = ((GhostRenderState) state).oxevyGetGhostEntity();
        if (!(entity instanceof GhostPlayerEntity ghost)) {
            original.call(collector, model, state, poseStack, renderType, light, overlay,
                    color, sprite, outlineColor, crumbling);
            return;
        }

        int alpha = Math.max(0, Math.min(255, Math.round(ghost.getGhostAlpha() * 255.0f)));
        original.call(collector, model, state, poseStack, renderType, light, overlay,
                (alpha << 24) | 0x00FFFFFF, sprite, outlineColor, crumbling);
    }

    @Inject(
        method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void oxevy$hideGhostNameTag(LivingEntity entity, double distanceToCameraSq,
            CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof GhostPlayerEntity) cir.setReturnValue(false);
    }
}

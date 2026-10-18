package me.alpha432.oxevy.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oxevy.cosmetics.BedrockCosmeticRenderer;
import me.alpha432.oxevy.manager.LocalCosmeticsManager;
import me.alpha432.oxevy.model.CosmeticModel;
import me.alpha432.oxevy.model.CosmeticModelLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererCosmetics {

    private static final CosmeticModelLoader modelLoader = CosmeticModelLoader.getInstance();

    /** Player attachment bones we know how to bind a cosmetic to, in preference order. */
    private static final String[] ATTACHMENT_BONES = {
        "head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"
    };

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V")
    )
    private void renderCosmetics(LivingEntityRenderState state, PoseStack poseStack,
                                 SubmitNodeCollector collector, CameraRenderState cameraRenderState,
                                 CallbackInfo ci) {
        if (state.entityType != EntityType.PLAYER) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!isCosmeticsEnabled()) return;

        var equipped = LocalCosmeticsManager.getEquipped();
        if (equipped.isEmpty()) return;

        int packedLight = state.lightCoords;

        // Head rotation (yaw/pitch) is not part of the body pose stack, so apply
        // it here so the cosmetic follows the head.
        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(state.yRot));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(state.xRot));

        for (String cosmeticId : equipped) {
            renderOneCosmetic(poseStack, collector, packedLight, cosmeticId, state.ageInTicks);
        }

        poseStack.popPose();
    }

    private void renderOneCosmetic(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                   String cosmeticId, float ageInTicks) {

        CosmeticModel cosmeticModel = modelLoader.getModel(cosmeticId);
        if (cosmeticModel == null) return;

        Identifier texture = modelLoader.getTextureLocation(cosmeticId);
        if (texture == null) return;

        BedrockCosmeticRenderer.render(poseStack, collector, cosmeticModel.getGeometry(),
                texture, packedLight, ageInTicks);
    }

    private boolean isCosmeticsEnabled() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return true;
    }
}
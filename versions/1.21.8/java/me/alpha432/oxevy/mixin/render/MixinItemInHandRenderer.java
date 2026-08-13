package me.alpha432.oxevy.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.HandCharmsModule;
import me.alpha432.oxevy.features.modules.render.HandViewModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void onRenderPlayerArm(PoseStack matrices, MultiBufferSource bufferSource, int light,
                                   float pitch, float tickProgress, HumanoidArm arm, CallbackInfo ci) {
        applyHandTransform(matrices, arm == HumanoidArm.LEFT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void onRenderItem(LivingEntity entity, ItemStack item, ItemDisplayContext context,
                              PoseStack matrices, MultiBufferSource bufferSource, int light, CallbackInfo ci) {
        if (!context.firstPerson()) return;
        applyHandTransform(matrices, context.leftHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    private void applyHandTransform(PoseStack matrices, InteractionHand hand) {
        // HandViewModule takes priority — position/rotation/scale per hand
        HandViewModule handView = Oxevy.moduleManager.getModuleByClass(HandViewModule.class);
        if (handView != null && handView.isEnabled()) {
            if (hand == InteractionHand.OFF_HAND) {
                matrices.translate(
                    handView.offPosX.getValue().floatValue(),
                    handView.offPosY.getValue().floatValue(),
                    handView.offPosZ.getValue().floatValue()
                );
                PoseStack.Pose pose = matrices.last();
                Matrix4f m = pose.pose();
                m.rotate((float) Math.toRadians(handView.offRotX.getValue()), 1F, 0F, 0F);
                m.rotate((float) Math.toRadians(handView.offRotY.getValue()), 0F, 1F, 0F);
                m.rotate((float) Math.toRadians(handView.offRotZ.getValue()), 0F, 0F, 1F);
                matrices.scale(
                    handView.offScaleX.getValue().floatValue(),
                    handView.offScaleY.getValue().floatValue(),
                    handView.offScaleZ.getValue().floatValue()
                );
            } else {
                matrices.translate(
                    handView.mainPosX.getValue().floatValue(),
                    handView.mainPosY.getValue().floatValue(),
                    handView.mainPosZ.getValue().floatValue()
                );
                PoseStack.Pose pose = matrices.last();
                Matrix4f m = pose.pose();
                m.rotate((float) Math.toRadians(handView.mainRotX.getValue()), 1F, 0F, 0F);
                m.rotate((float) Math.toRadians(handView.mainRotY.getValue()), 0F, 1F, 0F);
                m.rotate((float) Math.toRadians(handView.mainRotZ.getValue()), 0F, 0F, 1F);
                matrices.scale(
                    handView.mainScaleX.getValue().floatValue(),
                    handView.mainScaleY.getValue().floatValue(),
                    handView.mainScaleZ.getValue().floatValue()
                );
            }
            return;
        }

        // Fall back to HandCharmsModule
        HandCharmsModule handCharms = Oxevy.moduleManager.getModuleByClass(HandCharmsModule.class);
        if (handCharms != null && handCharms.shouldApply()) {
            matrices.translate(
                (float) handCharms.offsetX.getValue(),
                (float) handCharms.offsetY.getValue(),
                (float) handCharms.offsetZ.getValue()
            );

            PoseStack.Pose pose = matrices.last();
            Matrix4f matrix = pose.pose();
            matrix.rotate((float) Math.toRadians(handCharms.rotateX.getValue()), 1F, 0F, 0F);
            matrix.rotate((float) Math.toRadians(handCharms.rotateY.getValue()), 0F, 1F, 0F);
            matrix.rotate((float) Math.toRadians(handCharms.rotateZ.getValue()), 0F, 0F, 1F);

            matrices.scale(
                (float) handCharms.scale.getValue(),
                (float) handCharms.scale.getValue(),
                (float) handCharms.scale.getValue()
            );
        }
    }
}

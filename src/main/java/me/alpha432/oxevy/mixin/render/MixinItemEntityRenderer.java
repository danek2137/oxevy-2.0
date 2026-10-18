package me.alpha432.oxevy.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.ItemPhysicsModule;
import me.alpha432.oxevy.mixin.entity.MixinItemEntity;
import me.alpha432.oxevy.util.interfaces.ItemPhysicsRenderState;
import me.alpha432.oxevy.util.interfaces.ItemPhysicsSim;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the client-side item physics (bounce offset, tumble pitch, bob
 * control) computed by {@link MixinItemEntity} on top of the vanilla item
 * rendering. The sim values are copied onto the entity's render state in
 * extractRenderState, then applied while the pose is being built in submit.
 *
 * <p>Vanilla translate is {@code -bb.minY + 0.0625} (floor align) plus a sine
 * bob, and the model is spun continuously around Y. When the module is enabled
 * the vertical offset is replaced with {@code restY + bob + bounce} (anchoring
 * the model bottom to the ground) and the vanilla spin is skipped, so items
 * rest flat and only move with physics.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class MixinItemEntityRenderer {

    @Unique private ItemEntityRenderState oxevy$state;

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
        at = @At("TAIL"))
    private void oxevy$extract(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        ItemPhysicsRenderState s = (ItemPhysicsRenderState) (Object) state;
        ItemPhysicsModule m = Oxevy.moduleManager == null
                ? null
                : Oxevy.moduleManager.getModuleByClass(ItemPhysicsModule.class);

        if (m != null && m.isEnabled()) {
            ItemPhysicsSim sim = (ItemPhysicsSim) (Object) entity;
            float bounce = sim.oxevyGetBouncePrev()
                    + (sim.oxevyGetBounce() - sim.oxevyGetBouncePrev()) * partialTick;
            float pitch = sim.oxevyGetPitchPrev()
                    + (sim.oxevyGetPitch() - sim.oxevyGetPitchPrev()) * partialTick;

            float bob = 0f;
            if (m.bobbing.getValue()) {
                float speed = Math.max(0.01f, m.bobbingSpeed.getValue());
                bob = (float) (Math.sin((state.ageInTicks / 10.0) * speed + state.bobOffset) * 0.1 + 0.1);
            }

            float restY = -(float) state.item.getModelBoundingBox().minY - 0.0625f;

            s.oxevySetEnabled(true);
            s.oxevySetRestY(restY);
            s.oxevySetBob(bob);
            s.oxevySetBounce(bounce);
            s.oxevySetPitch(pitch);
        } else {
            s.oxevySetEnabled(false);
            s.oxevySetRestY(0f);
            s.oxevySetBob(0f);
            s.oxevySetBounce(0f);
            s.oxevySetPitch(0f);
        }
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("HEAD"))
    private void oxevy$submitHead(ItemEntityRenderState state, PoseStack pose,
                                  SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        oxevy$state = state;
    }

    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void oxevy$onTranslate(PoseStack pose, float x, float y, float z) {
        ItemPhysicsRenderState s = (ItemPhysicsRenderState) (Object) oxevy$state;
        if (s.oxevyGetEnabled()) {
            pose.translate(x, s.oxevyGetRestY() + s.oxevyGetBob() + s.oxevyGetBounce(), z);
        } else {
            pose.translate(x, y, z);
        }
    }

    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
    private void oxevy$onMulPose(PoseStack pose, Quaternionfc quat) {
        ItemPhysicsRenderState s = (ItemPhysicsRenderState) (Object) oxevy$state;
        if (s.oxevyGetEnabled()) {
            float pitch = s.oxevyGetPitch();
            if (pitch != 0f) {
                pose.mulPose(Axis.XP.rotation(pitch));
            }
        } else {
            pose.mulPose(quat);
        }
    }
}

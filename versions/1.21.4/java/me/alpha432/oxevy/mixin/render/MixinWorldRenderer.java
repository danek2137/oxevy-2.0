package me.alpha432.oxevy.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.alpha432.oxevy.util.render.RenderUtil;
import static me.alpha432.oxevy.util.traits.Util.EVENT_BUS;
import static me.alpha432.oxevy.util.traits.Util.mc;

@Mixin(LevelRenderer.class)
public class MixinWorldRenderer {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void preRender(CallbackInfo ci) {
        RenderUtil.invalidateCameraCache();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void render(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline,
                        Camera camera, GameRenderer gameRenderer, Matrix4f positionMatrix, Matrix4f projectionMatrix,
                        CallbackInfo ci, @Local ProfilerFiller profiler) {

        PoseStack stack = new PoseStack();
        stack.pushPose();
        stack.mulPose(Axis.XP.rotationDegrees(mc.gameRenderer.getMainCamera().getXRot()));
        stack.mulPose(Axis.YP.rotationDegrees(mc.gameRenderer.getMainCamera().getYRot() + 180f));

        profiler.push("oyvey-render-3d");

        Render3DEvent event = new Render3DEvent(stack, tickCounter.getGameTimeDeltaPartialTick(true), projectionMatrix, positionMatrix);
        EVENT_BUS.post(event);
        stack.popPose();
        profiler.pop();
    }
}
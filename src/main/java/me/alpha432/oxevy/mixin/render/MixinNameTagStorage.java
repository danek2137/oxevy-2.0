package me.alpha432.oxevy.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.NameTagsModule;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NameTagFeatureRenderer.Storage.class)
public class MixinNameTagStorage {
    @Inject(method = "add(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZIDLnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void onAdd(PoseStack matrices, @Nullable Vec3 vec3d, int i, Component text, boolean bl, int j, double d, CameraRenderState state, CallbackInfo ci) {
        NameTagsModule nameTags = Oxevy.moduleManager.getModuleByClass(NameTagsModule.class);
        if (nameTags != null && nameTags.isEnabled()) {
            ci.cancel();
        }
    }
}

package me.alpha432.oxevy.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.ChamsModule;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRenderer {

    private ChamsModule oxevy$chams() {
        if (Oxevy.moduleManager == null) return null;
        return Oxevy.moduleManager.getModuleByClass(ChamsModule.class);
    }

    @Redirect(
        method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
        )
    )
    private void redirectSubmitModelPart(SubmitNodeCollector instance, ModelPart modelPart,
                                         PoseStack poseStack, RenderType renderType,
                                         int light, int overlay, TextureAtlasSprite sprite) {
        ChamsModule chams = oxevy$chams();
        if (chams != null && chams.isEnabled() && chams.hand.getValue()) {
            RenderType finalType = chams.handTexture.getValue()
                ? renderType
                : RenderTypes.entityTranslucent(ChamsModule.BLANK);
            instance.submitModelPart(modelPart, poseStack, finalType, light, overlay, null,
                chams.handColor.getValue().getRGB(), null);
        } else {
            instance.submitModelPart(modelPart, poseStack, renderType, light, overlay, sprite);
        }
    }
}

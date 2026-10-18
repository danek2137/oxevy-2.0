package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.util.interfaces.GhostRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tags every extracted render state with the entity it came from, so the
 * living-entity renderer mixin can recognise Blink's ghosts during submission.
 *
 * <p>{@code EntityRenderer.extractRenderState} is always invoked through
 * {@code super} by the concrete renderers (the same hook already carries the
 * health/badge nametags), and it runs once per entity per frame, so the tag
 * stays correct even if render states are cached between frames.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererGhost {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void oxevy$tagGhostEntity(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (state == null) return;
        ((GhostRenderState) state).oxevySetGhostEntity(entity);
    }
}

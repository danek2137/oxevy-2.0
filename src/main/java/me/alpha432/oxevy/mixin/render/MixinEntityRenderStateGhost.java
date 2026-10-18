package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.util.interfaces.GhostRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Carries the entity a render state was extracted from alongside the state.
 *
 * <p>Extraction and submission are separate phases, so the Blink ghost alpha
 * has to be resolved from the entity when the model is submitted, not when the
 * state is built - that keeps it in sync with fades driven by the module's
 * own tick.
 */
@Mixin(EntityRenderState.class)
public abstract class MixinEntityRenderStateGhost implements GhostRenderState {

    @Unique private Entity oxevy$ghostEntity;

    @Override
    public void oxevySetGhostEntity(Entity entity) {
        oxevy$ghostEntity = entity;
    }

    @Override
    public Entity oxevyGetGhostEntity() {
        return oxevy$ghostEntity;
    }
}

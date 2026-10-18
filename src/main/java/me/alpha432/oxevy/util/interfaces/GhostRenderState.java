package me.alpha432.oxevy.util.interfaces;

import net.minecraft.world.entity.Entity;

/**
 * Implemented by the {@code EntityRenderState} mixin so the living-entity
 * renderer mixin can tell which entity a render state was extracted from.
 *
 * <p>The render pipeline is split in two phases: every entity's state is
 * extracted first, and the states are submitted to the GPU later. The entity
 * itself is not available during the submission phase, so it has to travel
 * with the state object. This mirrors the existing
 * {@link ItemPhysicsRenderState} pattern used by the item-physics feature.
 *
 * <p>The interface deliberately lives outside the {@code mixin} package:
 * Mixin rejects casting to a {@code @Mixin} class from inside injected code,
 * so both mixins talk through this interface instead.
 */
public interface GhostRenderState {
    void oxevySetGhostEntity(Entity entity);
    Entity oxevyGetGhostEntity();
}

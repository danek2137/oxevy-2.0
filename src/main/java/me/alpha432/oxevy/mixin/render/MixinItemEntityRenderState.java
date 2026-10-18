package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.util.interfaces.ItemPhysicsRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Carries the client-side item physics values computed by
 * {@link me.alpha432.oxevy.mixin.entity.MixinItemEntity} alongside each
 * entity's render state. The extraction and submission phases are batched
 * (all states are extracted, then all states are submitted), so the sim data
 * must travel with the state object instead of living on the renderer.
 */
@Mixin(ItemEntityRenderState.class)
public abstract class MixinItemEntityRenderState implements ItemPhysicsRenderState {

    @Unique private boolean oxevy$enabled;
    @Unique private float oxevy$restY;
    @Unique private float oxevy$bob;
    @Unique private float oxevy$bounce;
    @Unique private float oxevy$pitch;

    @Override
    public void oxevySetEnabled(boolean enabled) { oxevy$enabled = enabled; }

    @Override
    public boolean oxevyGetEnabled() { return oxevy$enabled; }

    @Override
    public void oxevySetRestY(float restY) { oxevy$restY = restY; }

    @Override
    public float oxevyGetRestY() { return oxevy$restY; }

    @Override
    public void oxevySetBob(float bob) { oxevy$bob = bob; }

    @Override
    public float oxevyGetBob() { return oxevy$bob; }

    @Override
    public void oxevySetBounce(float bounce) { oxevy$bounce = bounce; }

    @Override
    public float oxevyGetBounce() { return oxevy$bounce; }

    @Override
    public void oxevySetPitch(float pitch) { oxevy$pitch = pitch; }

    @Override
    public float oxevyGetPitch() { return oxevy$pitch; }
}

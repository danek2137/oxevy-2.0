package me.alpha432.oxevy.util.interfaces;

/**
 * Shared interface implemented by {@link me.alpha432.oxevy.mixin.entity.MixinItemEntity}
 * so the renderer mixin can read the client-side physics simulation without
 * depending on @Unique member names staying stable. Lives outside the mixin
 * package so Mixin allows it to be referenced as a plain class.
 */
public interface ItemPhysicsSim {
    float oxevyGetBounce();
    float oxevyGetBouncePrev();
    float oxevyGetPitch();
    float oxevyGetPitchPrev();
}

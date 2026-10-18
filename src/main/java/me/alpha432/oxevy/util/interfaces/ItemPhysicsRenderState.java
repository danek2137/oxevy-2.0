package me.alpha432.oxevy.util.interfaces;

/**
 * Shared interface implemented by the {@code ItemEntityRenderState} mixin so the
 * item renderer mixin can read/write the client-side physics values without
 * depending on @Unique member names or casting to a @Mixin class (which Mixin
 * rejects inside injected code). Lives outside the mixin package on purpose.
 *
 * <p>The vertical offset in {@code ItemEntityRenderer.submit} is replaced
 * entirely: {@code restY} anchors the model bottom to the ground (replacing
 * vanilla's {@code -bb.minY + 0.0625} float), then module {@code bob} and
 * {@code bounce} are layered on top. The vanilla Y-axis spin is skipped while
 * the module is enabled. When disabled, all values are 0 and vanilla rendering
 * is restored exactly.
 */
public interface ItemPhysicsRenderState {
    void oxevySetEnabled(boolean enabled);
    boolean oxevyGetEnabled();
    void oxevySetRestY(float restY);
    float oxevyGetRestY();
    void oxevySetBob(float bob);
    float oxevyGetBob();
    void oxevySetBounce(float bounce);
    float oxevyGetBounce();
    void oxevySetPitch(float pitch);
    float oxevyGetPitch();
}

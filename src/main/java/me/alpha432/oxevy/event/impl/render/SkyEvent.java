package me.alpha432.oxevy.event.impl.render;

import me.alpha432.oxevy.event.Event;

/**
 * Fired by {@link me.alpha432.oxevy.mixin.render.MixinSkyRenderer} at the head of
 * {@code SkyRenderer.extractRenderState}. Modules (e.g. {@link
 * me.alpha432.oxevy.features.modules.render.CustomSkyModule}) subscribe to it and
 * override the sky color, fog color/distance and sun/moon/star visibility, then
 * cancel the event so the mixin applies the values.
 *
 * <p>This mirrors the Soup Visuals approach: a static singleton event is reset,
 * posted to the bus, then consumed by the mixin that injected into the vanilla
 * renderer.</p>
 */
public class SkyEvent extends Event {
    public static final SkyEvent INSTANCE = new SkyEvent();

    private int skyColor = -1;
    private int fogColor = -1;
    private float fogDistance = -1.0f;
    private boolean hideSun;
    private boolean hideMoon;
    private boolean hideStars;

    public SkyEvent reset() {
        this.skyColor = -1;
        this.fogColor = -1;
        this.fogDistance = -1.0f;
        this.hideSun = false;
        this.hideMoon = false;
        this.hideStars = false;
        setCancelled(false);
        return this;
    }

    public int getSkyColor() {
        return skyColor;
    }

    public void setSkyColor(int skyColor) {
        this.skyColor = skyColor;
    }

    public boolean hasSkyColor() {
        return skyColor != -1;
    }

    public int getFogColor() {
        return fogColor;
    }

    public void setFogColor(int fogColor) {
        this.fogColor = fogColor;
    }

    public boolean hasFogColor() {
        return fogColor != -1;
    }

    public float getFogDistance() {
        return fogDistance;
    }

    public void setFogDistance(float fogDistance) {
        this.fogDistance = fogDistance;
    }

    public boolean hasFogDistance() {
        return fogDistance > 0.0f;
    }

    public boolean shouldHideSun() {
        return hideSun;
    }

    public void setHideSun(boolean hideSun) {
        this.hideSun = hideSun;
    }

    public boolean shouldHideMoon() {
        return hideMoon;
    }

    public void setHideMoon(boolean hideMoon) {
        this.hideMoon = hideMoon;
    }

    public boolean shouldHideStars() {
        return hideStars;
    }

    public void setHideStars(boolean hideStars) {
        this.hideStars = hideStars;
    }

    public boolean shouldHideCelestial() {
        return hideSun || hideMoon || hideStars;
    }
}

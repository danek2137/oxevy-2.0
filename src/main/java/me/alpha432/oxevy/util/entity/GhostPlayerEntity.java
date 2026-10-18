package me.alpha432.oxevy.util.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A client-side copy of the local player used by the Blink module.
 *
 * <p>It is a real entity so the vanilla render pipeline draws it for free -
 * skin, model, held items, pose and translucency all come from the normal
 * player renderer - but it is inert and untouchable:
 *
 * <ul>
 *     <li>{@link #tick()} only re-syncs the interpolation fields, so a ghost
 *     never falls, never walks and never recalculates its pose. It stays
 *     exactly where Blink spawned it.</li>
 *     <li>{@link #isPickable()} is {@code false}, so crosshair ray-traces
 *     never resolve to a ghost. That covers {@code mc.hitResult},
 *     {@code crosshairPickEntity} and therefore every click based module
 *     (TriggerBot, AutoClicker, MCF, TargetHud).</li>
 *     <li>{@link #isPushable()} and {@link #canBeCollidedWith(Entity)} are
 *     {@code false} and {@code noPhysics} is set, so you walk through your own
 *     ghosts instead of bumping into them.</li>
 *     <li>{@link #shouldShowName()} is {@code false}, so no vanilla nametag
 *     floats over the trail.</li>
 * </ul>
 *
 * <p>Combat target selection additionally skips ghosts through
 * {@link #isGhost(Entity)}.
 *
 * <p>A unique random UUID is used on purpose: re-using the local player's id
 * would replace the real player inside the level's entity lookup, which is
 * keyed by uuid.
 */
public class GhostPlayerEntity extends RemotePlayer {

    private float ghostAlpha = 0.35f;
    private long spawnTime;

    public GhostPlayerEntity(ClientLevel level, GameProfile profile) {
        super(level, profile);
        this.noPhysics = true;
        this.setInvulnerable(true);
        this.setNoGravity(true);
    }

    /** @return {@code true} if this entity is one of Blink's fake players. */
    public static boolean isGhost(Entity entity) {
        return entity instanceof GhostPlayerEntity;
    }

    public float getGhostAlpha() {
        return ghostAlpha;
    }

    public void setGhostAlpha(float ghostAlpha) {
        this.ghostAlpha = ghostAlpha;
    }

    public long getSpawnTime() {
        return spawnTime;
    }

    public void setSpawnTime(long spawnTime) {
        this.spawnTime = spawnTime;
    }

    @Override
    public void tick() {
        // A frozen snapshot: no AI, no gravity, no pose recalculation. The
        // interpolation fields still have to be refreshed every tick or the
        // renderer would lerp the model from the origin, and yBodyRot/yHeadRot
        // have to stay equal or solveBodyRot() would keep swinging the body.
        this.setOldPosAndRot();
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public PlayerSkin getSkin() {
        // The random uuid misses the tab list, which would fall back to the
        // default Steve/Alex skin. Borrow the local player's skin instead so
        // the ghost looks like a copy of you.
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) return client.player.getSkin();
        return super.getSkin();
    }
}

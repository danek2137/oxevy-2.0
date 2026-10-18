package me.alpha432.oxevy.features.modules.movement;

import com.mojang.authlib.GameProfile;
import me.alpha432.oxevy.event.impl.network.PacketEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.entity.GhostPlayerEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake lag: every movement packet is held before it leaves the client, so the
 * server keeps seeing the position from the moment Blink was enabled, while a
 * trail of translucent {@link GhostPlayerEntity} copies marks the path you are
 * really walking.
 *
 * <p>One ghost is spawned per game tick for every tick in which at least one
 * packet was held, so the trail matches the movement rather than the packet
 * rate (movement packets are only sent when you actually move).
 */
public class BlinkModule extends Module {

    /** How often held packets are flushed to the server. 0 = hold until disabled. */
    public final Setting<Integer> fakeLagDelay = num("FakeLagDelay", 3000, 0, 10000);
    public final Setting<Float> ghostAlpha = num("GhostAlpha", 0.35f, 0.05f, 0.8f);
    public final Setting<Boolean> showGhost = bool("ShowGhost", true);
    /**
     * Blocks of held movement between two ghosts. 0 = one ghost per lag window,
     * dropped where you really are the moment the server catches up. Higher
     * values spread the same idea into a dotted trail along your path.
     */
    public final Setting<Float> ghostEvery = num("GhostEvery", 0f, 0f, 10f);
    /** How long a ghost stays before fading out. 0 = until the module is disabled. */
    public final Setting<Integer> ghostLifetime = num("GhostLifetime", 0, 0, 20000);
    public final Setting<Integer> maxGhosts = num("MaxGhosts", 24, 1, 200);

    /** Ghosts currently spawned, oldest first. */
    private final List<GhostPlayerEntity> ghosts = new ArrayList<>();

    /** Movement packets swallowed since the last game tick. */
    private final AtomicInteger heldThisTick = new AtomicInteger();

    /** True while flushing, so Blink never swallows its own catch-up packet. */
    private volatile boolean flushing;
    /** True once any packet was held inside the current lag window. */
    private volatile boolean heldAnyInWindow;
    private boolean holding;
    private long lastFlush;
    private ClientLevel ghostLevel;
    private Vec3 lastGhostPos;

    public BlinkModule() {
        super("Blink", "Fake lag with transparent ghost player", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        initialize();
    }

    private void initialize() {
        ghosts.clear();
        heldThisTick.set(0);
        heldAnyInWindow = false;
        flushing = false;
        holding = true;
        ghostLevel = mc.level;
        lastGhostPos = null;
        lastFlush = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        // The event bus is already unregistered at this point (Module.disable
        // unregisters first), so the catch-up packet below always leaves.
        holding = false;
        clearGhosts();
        flush();
        heldThisTick.set(0);
        lastGhostPos = null;
    }

    /**
     * Runs for every packet leaving the client. While Blink is enabled every
     * movement packet is cancelled here, which is what makes the server believe
     * you are still standing where you were when you enabled the module.
     *
     * <p>A negative priority puts this listener behind every other packet
     * module, so modules such as AimBot still get to rewrite the silent
     * rotation of a movement packet before it is held.
     */
    @Subscribe(priority = -100)
    public void onPacketSend(PacketEvent.Send event) {
        if (!holding || flushing) return;
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket)) return;

        event.cancel();
        heldThisTick.incrementAndGet();
        heldAnyInWindow = true;
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        // Was enabled before a world was loaded (e.g. from the config at startup)
        if (!holding) initialize();

        // Ghosts only belong to the level that created them
        if (mc.level != ghostLevel) {
            clearGhosts();
            ghostLevel = mc.level;
            lastGhostPos = null;
        }

        int delay = fakeLagDelay.getValue();
        boolean windowClosed = holding && delay > 0 && System.currentTimeMillis() - lastFlush >= delay;

        if (showGhost.getValue()) {
            float every = ghostEvery.getValue();
            if (every > 0f) {
                // Trail mode: one ghost every GhostEvery blocks of held movement
                if (heldThisTick.getAndSet(0) > 0) {
                    Vec3 pos = mc.player.position();
                    if (lastGhostPos == null || lastGhostPos.distanceTo(pos) >= every) spawnGhost();
                }
            } else if (windowClosed && heldAnyInWindow) {
                // One ghost per lag window, at the place you really reached
                spawnGhost();
            }
        }

        if (windowClosed) flush();

        updateGhosts();
    }

    /**
     * Drops a copy of the local player exactly where the client was while its
     * movement packets were being held.
     */
    private void spawnGhost() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;

        Vec3 pos = player.position();
        // Don't pile ghosts on top of each other while standing still
        if (lastGhostPos != null && lastGhostPos.distanceToSqr(pos) < 0.0009) return;

        GhostPlayerEntity ghost = new GhostPlayerEntity(level,
                new GameProfile(UUID.randomUUID(), player.getGameProfile().name()));

        ghost.copyPosition(player);
        ghost.setYRot(player.getYRot());
        ghost.setXRot(player.getXRot());
        ghost.setYHeadRot(player.yHeadRot);
        ghost.setYBodyRot(player.yBodyRot);
        ghost.setOldPosAndRot();
        ghost.setPose(player.getPose());
        ghost.setGhostAlpha(ghostAlpha.getValue());
        ghost.setSpawnTime(System.currentTimeMillis());

        level.addEntity(ghost);
        ghosts.add(ghost);
        lastGhostPos = pos;

        while (ghosts.size() > Math.max(1, maxGhosts.getValue())) {
            removeGhost(ghosts.remove(0));
        }
    }

    /** Fades ghosts out and removes them once their lifetime is over. */
    private void updateGhosts() {
        if (ghosts.isEmpty()) return;

        int lifetime = ghostLifetime.getValue();
        float base = ghostAlpha.getValue();
        long now = System.currentTimeMillis();

        Iterator<GhostPlayerEntity> it = ghosts.iterator();
        while (it.hasNext()) {
            GhostPlayerEntity ghost = it.next();

            // The level may have dropped it (e.g. after a respawn)
            if (ghost.isRemoved()) {
                it.remove();
                continue;
            }

            if (lifetime <= 0) {
                ghost.setGhostAlpha(base);
                continue;
            }

            long age = now - ghost.getSpawnTime();
            if (age >= lifetime) {
                removeGhost(ghost);
                it.remove();
                continue;
            }

            float fade = 1.0f - (float) age / (float) lifetime;
            ghost.setGhostAlpha(Math.max(0.02f, base * fade));
        }
    }

    /**
     * Hands the server a current position packet, ending one fake lag window.
     * {@link #flushing} keeps {@link #onPacketSend} from holding it again.
     */
    private void flush() {
        lastFlush = System.currentTimeMillis();
        heldAnyInWindow = false;

        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) return;

        flushing = true;
        try {
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(),
                    player.onGround(), player.horizontalCollision));
        } finally {
            flushing = false;
        }
    }

    private void clearGhosts() {
        for (GhostPlayerEntity ghost : ghosts) removeGhost(ghost);
        ghosts.clear();
    }

    private void removeGhost(GhostPlayerEntity ghost) {
        ClientLevel level = ghostLevel;
        if (level != null && !ghost.isRemoved()) {
            level.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    @Override
    public String getDisplayInfo() {
        if (!holding) return "Idle";
        return ghosts.isEmpty() ? "Holding" : ghosts.size() + " ghosts";
    }
}

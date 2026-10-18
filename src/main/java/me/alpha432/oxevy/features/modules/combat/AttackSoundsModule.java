package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.event.impl.network.PacketEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.sound.SoundMode;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class AttackSoundsModule extends Module {
    private final Setting<SoundMode> hitSound = mode("HitSound", SoundMode.SKEET);
    private final Setting<SoundMode> critSound = mode("CritSound", SoundMode.RUST_HEADSHOT);
    private final Setting<SoundMode> killSound = mode("KillSound", SoundMode.RUST_HEADSHOT);
    private final Setting<Double> volume = num("Volume", 1.0, 0.0, 2.0);
    private final Setting<Double> pitch = num("Pitch", 1.0, 0.5, 2.0);
    private final Setting<Boolean> onlyPlayers = bool("OnlyPlayers", false);

    private final Map<Integer, Long> recentlyHit = new HashMap<>();
    private int pendingCritEntity = -1;

    private static final long RECENT_HIT_TIMEOUT_MS = 2000L;

    public AttackSoundsModule() {
        super("AttackSounds", "Plays hit, crit and kill sounds", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (!recentlyHit.isEmpty()) {
            long now = System.currentTimeMillis();
            recentlyHit.entrySet().removeIf(e -> now - e.getValue() > RECENT_HIT_TIMEOUT_MS);
        }
    }

    @Override
    public void onDisable() {
        recentlyHit.clear();
        pendingCritEntity = -1;
    }

    @Subscribe
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundInteractPacket packet)) return;
        if (packet.action.getType() != ServerboundInteractPacket.ActionType.ATTACK) return;
        if (mc.player == null || mc.level == null) return;

        pendingCritEntity = -1;
        Entity entity = mc.level.getEntity(packet.entityId);
        if (entity instanceof LivingEntity && isCritConditions()) {
            pendingCritEntity = packet.entityId;
        }
    }

    @Subscribe
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        if (event.getPacket() instanceof ClientboundDamageEventPacket packet) {
            if (packet.sourceCauseId() != mc.player.getId() && packet.sourceDirectId() != mc.player.getId()) {
                return;
            }
            Entity victim = mc.level.getEntity(packet.entityId());
            if (victim == null || victim.equals(mc.player)) return;
            if (onlyPlayers.getValue() && !(victim instanceof Player)) return;

            recentlyHit.put(packet.entityId(), System.currentTimeMillis());

            if (pendingCritEntity == packet.entityId()) {
                pendingCritEntity = -1;
                play(critSound.getValue());
            } else {
                play(hitSound.getValue());
            }
            return;
        }

        if (event.getPacket() instanceof ClientboundEntityEventPacket packet && packet.getEventId() == 3) {
            Entity dead = packet.getEntity(mc.level);
            if (dead == null || dead.equals(mc.player)) return;
            Long last = recentlyHit.remove(dead.getId());
            if (last == null || System.currentTimeMillis() - last > RECENT_HIT_TIMEOUT_MS) return;
            if (onlyPlayers.getValue() && !(dead instanceof Player)) return;

            play(killSound.getValue());
        }
    }

    private boolean isCritConditions() {
        return mc.player.fallDistance > 0.0F
                && !mc.player.onGround()
                && !mc.player.onClimbable()
                && !mc.player.isInWater()
                && !mc.player.isMobilityRestricted()
                && !mc.player.isPassenger()
                && !mc.player.isSprinting();
    }

    private void play(SoundMode mode) {
        if (mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(
                mode.getSound(), pitch.getValue().floatValue(), volume.getValue().floatValue()));
    }
}

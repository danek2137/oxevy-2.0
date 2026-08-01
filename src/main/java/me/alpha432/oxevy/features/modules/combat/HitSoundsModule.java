package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.event.impl.network.PacketEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.sound.OxevySounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class HitSoundsModule extends Module {
    private final Setting<SoundMode> mode = mode("Sound", SoundMode.SKEET);
    private final Setting<Double> volume = num("Volume", 1.0, 0.0, 2.0);
    private final Setting<Double> pitch = num("Pitch", 1.0, 0.5, 2.0);
    private final Setting<Boolean> onlyPlayers = bool("OnlyPlayers", false);

    public HitSoundsModule() {
        super("HitSounds", "Plays a sound when you hit an entity", Category.COMBAT);
    }

    @Subscribe
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundInteractPacket packet
                && packet.action.getType() == ServerboundInteractPacket.ActionType.ATTACK) {
            Entity entity = mc.level.getEntity(packet.entityId);
            if (entity == null) return;
            if (onlyPlayers.getValue() && !(entity instanceof LivingEntity)) return;

            SoundEvent sound = switch (mode.getValue()) {
                case SKEET -> OxevySounds.SKEET;
                case BOOLEAN -> OxevySounds.BOOLEAN;
                case ENABLE -> OxevySounds.ENABLE;
                case DISABLE -> OxevySounds.DISABLE;
                case KEYPRESS -> OxevySounds.KEYPRESS;
                case KEYRELEASE -> OxevySounds.KEYRELEASE;
                case SCROLL -> OxevySounds.SCROLL;
                case SWIPEIN -> OxevySounds.SWIPEIN;
                case SWIPEOUT -> OxevySounds.SWIPEOUT;
                case PMSOUND -> OxevySounds.PMSOUND;
                case ORTHODOX -> OxevySounds.ORTHODOX;
                case UWU -> OxevySounds.UWU;
                case MOAN1 -> OxevySounds.MOAN1;
                case MOAN2 -> OxevySounds.MOAN2;
                case MOAN3 -> OxevySounds.MOAN3;
                case MOAN4 -> OxevySounds.MOAN4;
            };

            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound,
                        pitch.getValue().floatValue(), volume.getValue().floatValue()));
            }
        }
    }

    private enum SoundMode {
        SKEET, BOOLEAN, ENABLE, DISABLE, KEYPRESS, KEYRELEASE,
        SCROLL, SWIPEIN, SWIPEOUT, PMSOUND, ORTHODOX, UWU,
        MOAN1, MOAN2, MOAN3, MOAN4
    }
}

package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.event.impl.network.PacketEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

public class CriticalsModule extends Module {
    public CriticalsModule() {
        super("Criticals", "Makes you do critical hits", Category.COMBAT);
    }

    @Subscribe
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundInteractPacket packet && packet.action.getType() == ServerboundInteractPacket.ActionType.ATTACK) {
            Entity entity = mc.level.getEntity(packet.entityId);
            if (entity == null
                    || entity instanceof EndCrystal
                    || !mc.player.onGround()
                    || !(entity instanceof LivingEntity)) return;

            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.1f, mc.player.getZ(), false));
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
            mc.player.crit(entity);
        }
    }

    @Override
    public String getDisplayInfo() {
        return "Packet";
    }
}

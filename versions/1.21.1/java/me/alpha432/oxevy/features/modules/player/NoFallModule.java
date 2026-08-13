package me.alpha432.oxevy.features.modules.player;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.Module;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFallModule extends Module {
    public NoFallModule() {
        super("NoFall", "Removes fall damage", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (!mc.player.onGround() && Oxevy.positionManager.getFallDistance() > 3) {
            ServerboundMovePlayerPacket.PosRot pakcet = new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY() + 0.000000001, mc.player.getZ(),
                    mc.player.getYRot(), mc.player.getXRot(), false);
            mc.player.connection.send(pakcet);
        }
    }
}

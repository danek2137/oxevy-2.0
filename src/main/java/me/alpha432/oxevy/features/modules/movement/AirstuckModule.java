package me.alpha432.oxevy.features.modules.movement;

import me.alpha432.oxevy.features.modules.Module;
import net.minecraft.world.phys.Vec3;

public class AirstuckModule extends Module {

    private Vec3 frozenPos;

    public AirstuckModule() {
        super("Airstuck", "Freezes you in the air at your current position", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        frozenPos = mc.player.position();
        mc.player.setNoGravity(true);
        mc.player.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        mc.player.setNoGravity(false);
        frozenPos = null;
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setNoGravity(true);
        if (frozenPos != null) {
            mc.player.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
        }
    }
}

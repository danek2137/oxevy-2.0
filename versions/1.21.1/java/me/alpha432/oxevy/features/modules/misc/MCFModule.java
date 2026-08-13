package me.alpha432.oxevy.features.modules.misc;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.modules.Module;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public class MCFModule extends Module {
    private boolean pressed;

    public MCFModule() {
        super("Friends Add", "Middle click friend", Category.MISC);
    }

    @Override
    public void onTick() {
        if (GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), 2) == 1) {
            if (!pressed) click();
            pressed = true;
        } else {
            pressed = false;
        }
    }

    private void click() {
        Entity targetedEntity = mc.crosshairPickEntity;
        if (!(targetedEntity instanceof Player)) return;
        String name = ((Player) targetedEntity).getGameProfile().getName();

        if (Oxevy.friendManager.isFriend(name)) {
            Oxevy.friendManager.removeFriend(name);

            Command.sendMessage("{red} %s has been unfriended.", name);
        } else {
            Oxevy.friendManager.addFriend(name);
            Command.sendMessage("{aqua} %s has been friended.", name);
        }
    }
}

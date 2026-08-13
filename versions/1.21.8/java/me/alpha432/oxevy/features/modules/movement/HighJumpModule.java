package me.alpha432.oxevy.features.modules.movement;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.lwjgl.glfw.GLFW;

public class HighJumpModule extends Module {
    public final Setting<Float> height = num("Height", 1.5f, 1.0f, 10.0f);
    public final Setting<Boolean> onlyOnGround = bool("OnlyOnGround", true);

    public HighJumpModule() {
        super("HighJump", "Jump higher", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (onlyOnGround.getValue() && !mc.player.onGround()) return;
        boolean jumping = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE) == 1;
        if (jumping) {
            mc.player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(height.getValue());
        }
    }
}
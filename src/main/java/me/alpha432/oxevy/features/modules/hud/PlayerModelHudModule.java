/*
 * Player model rendering approach adapted from Meteor Client (https://github.com/MeteorDevelopment/meteor-client)
 * Original code in meteordevelopment.meteorclient.systems.hud.HudRenderer.entity()
 */
package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlayerModelHudModule extends HudModule {
    private final Setting<Float> dollScale = num("Doll Scale", 2.0f, 1.0f, 4.0f);
    private final Setting<Boolean> showName = bool("Show Name", true);
    private final Setting<Boolean> autoRotate = bool("Auto Rotate", true);

    public PlayerModelHudModule() {
        super("PlayerModel", "Shows a 3D paper doll of your character", 60, 100);
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (mc.player == null) return;

        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();
        float s = dollScale.getValue();

        int dollW = (int) (50 * s);
        int dollH = (int) (70 * s);
        int nameH = showName.getValue() ? mc.font.lineHeight + 4 : 0;
        setWidth(dollW);
        setHeight(dollH + nameH);

        LivingEntity player = mc.player;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        float yaw;
        if (autoRotate.getValue()) {
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            yaw = -Mth.wrapDegrees(player.yBodyRot + (player.getYRot() - player.yBodyRot) * partialTick);
        } else {
            yaw = -player.getYRot();
        }

        float pitch = -player.getXRot();

        // Save entity state
        float prevBodyRot = player.yBodyRot;
        float prevYaw = player.getYRot();
        float prevPitch = player.getXRot();
        float prevHeadRot = player.yHeadRot;
        float prevHeadRotO = player.yHeadRotO;

        // Set rotations for rendering (same as Meteor Client)
        float tanYaw = (float) Math.atan(yaw / 40.0f);
        float tanPitch = (float) Math.atan(pitch / 40.0f);
        player.yBodyRot = 180.0f + tanYaw * 20.0f;
        player.setYRot(180.0f + tanYaw * 40.0f);
        player.setXRot(-tanPitch * 20.0f);
        player.yHeadRot = player.getYRot();
        player.yHeadRotO = player.getYRot();

        try {
            EntityRenderState state = dispatcher.extractEntity(player, 1.0f);

            float scale = Math.max(dollW, dollH) / 2f;
            Vector3f translation = new Vector3f(0, 1f, 0);
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);

            ctx.submitEntityRenderState(state, scale, translation, rotation, null,
                    (int) x, (int) y, (int) (x + dollW), (int) (y + dollH));
        } catch (Exception ignored) {
        }

        // Restore entity state
        player.yBodyRot = prevBodyRot;
        player.setYRot(prevYaw);
        player.setXRot(prevPitch);
        player.yHeadRot = prevHeadRot;
        player.yHeadRotO = prevHeadRotO;

        if (showName.getValue()) {
            String name = mc.player.getName().getString();
            int textW = mc.font.width(name);
            int textX = (int) (x + (dollW - textW) / 2f);
            int textY = (int) (y + dollH + 2);
            ctx.drawString(mc.font, name, textX, textY, 0xFFDDDDDD, true);
        }
    }
}

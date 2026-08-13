package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import me.alpha432.oxevy.util.ColorUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Camera;

import java.awt.Color;

/**
 * TracerModule renders tracers from the player's eyes to nearby entities.
 * Color encodes relationship: friends green, allies teal, others red.
 */
public class TracerModule extends Module {
    public final Setting<Float> maxDistance = num("MaxDistance", 100f, 16f, 256f);
    public final Setting<Float> lineWidth = num("LineWidth", 1.0f, 0.5f, 5f);
    public final Setting<Boolean> throughWalls = bool("ThroughWalls", true);

    public TracerModule() {
        super("Tracer", "Draws tracers to nearby entities", Category.RENDER);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 from = RenderUtil.getTracerOrigin();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;

            // Use float distance for consistency with our Float setting
            float dist = (float) camera.distanceTo(entity.position());
            if (dist > maxDistance.getValue()) continue;

            Color color = getColor(entity);
            Vec3 to = entity.position().add(0, entity.getBbHeight() * 0.5, 0);

            RenderUtil.drawLine(event.getMatrix(), from, to, color, lineWidth.getValue(), throughWalls.getValue());
        }
    }

    private Color getColor(Entity entity) {
        if (entity instanceof Player player) {
            if (Oxevy.friendManager.isFriend(player)) return new Color(0, 255, 0, 255); // green for friends
            if (mc.player.isAlliedTo(player)) return new Color(100, 255, 180, 255); // teal for allies
            return new Color(255, 80, 80, 255); // red for others
        }
        if (entity instanceof LivingEntity) {
            return new Color(255, 0, 255, 255); // magenta for living ents
        }
        return new Color(255, 255, 255, 255); // white for others
    }
}

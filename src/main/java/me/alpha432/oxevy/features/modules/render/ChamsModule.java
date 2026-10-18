package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.awt.Color;

public class ChamsModule extends Module {

    public static final Identifier BLANK = Identifier.fromNamespaceAndPath("oxevy", "textures/blank.png");

    public Setting<Boolean> players = bool("Players", false);
    public Setting<Boolean> playerTexture = bool("PlayerTexture", false);
    public Setting<Color> playerColor = color("PlayerColor", 198, 135, 254, 150);
    public Setting<Double> playerScale = num("PlayerScale", 1.0, 0.0, 3.0);

    public Setting<Boolean> crystals = bool("Crystals", false);
    public Setting<Color> crystalColor = color("CrystalColor", 198, 135, 254, 255);
    public Setting<Double> crystalScale = num("CrystalScale", 0.6, 0.0, 3.0);

    public Setting<Boolean> hand = bool("Hand", false);
    public Setting<Boolean> handTexture = bool("HandTexture", false);
    public Setting<Color> handColor = color("HandColor", 198, 135, 254, 150);

    public ChamsModule() {
        super("Chams", "Tweaks rendering of entities", Category.RENDER);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;

            if (players.getValue() && entity.getType() == EntityType.PLAYER) {
                AABB box = getLerpedBox(entity, event.getDelta());
                Color color = playerColor.getValue();
                if (!playerTexture.getValue()) {
                    RenderUtil.drawSolidBoxes(event.getMatrix(), java.util.List.of(box), color.getRGB(), true);
                }
                RenderUtil.drawOutlinedBoxes(event.getMatrix(), java.util.List.of(box), color.getRGB(), true);
            }

            if (crystals.getValue() && entity.getType() == EntityType.END_CRYSTAL) {
                AABB box = getLerpedBox(entity, event.getDelta());
                Color color = crystalColor.getValue();
                RenderUtil.drawOutlinedBoxes(event.getMatrix(), java.util.List.of(box), color.getRGB(), true);
            }
        }
    }

    private AABB getLerpedBox(Entity entity, float delta) {
        double x = net.minecraft.util.Mth.lerp(delta, entity.xo, entity.getX());
        double y = net.minecraft.util.Mth.lerp(delta, entity.yo, entity.getY());
        double z = net.minecraft.util.Mth.lerp(delta, entity.zo, entity.getZ());
        double w = entity.getBbWidth() / 2;
        double h = entity.getBbHeight();
        return new AABB(x - w, y, z - w, x + w, y + h, z + w);
    }
}

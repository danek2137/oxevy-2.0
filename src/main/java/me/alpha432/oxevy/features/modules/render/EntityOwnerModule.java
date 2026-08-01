package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.NametagUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EntityOwnerModule extends Module {

    public Setting<Double> textScale = num("Scale", 1.0, 0.1, 5.0);
    public Setting<Color> nameColor = color("NameColor", new Color(255, 255, 255, 255));
    public Setting<Boolean> shadow = bool("Shadow", true);

    private final Vector3d pos = new Vector3d();
    private final Map<UUID, String> ownerCache = new HashMap<>();

    public EntityOwnerModule() {
        super("EntityOwner", "Shows the owner of tamed entities", Category.RENDER);
    }

    @Override
    public void onDisable() {
        ownerCache.clear();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        NametagUtils.onRender(event.getProjectionMatrix(), event.getPositionMatrix());
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (nullCheck()) return;

        GuiGraphics ctx = event.getContext();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof TamableAnimal tamable)) continue;

            var ownerRef = tamable.getOwnerReference();
            if (ownerRef == null) continue;

            UUID ownerUUID = ownerRef.getUUID();
            if (ownerUUID == null) continue;

            String ownerName = getOwnerName(ownerUUID, entity);
            if (ownerName == null) continue;

            pos.set(entity.getX(), entity.getY() + entity.getBbHeight() + 0.5, entity.getZ());

            if (!NametagUtils.to2D(pos, textScale.getValue())) continue;

            NametagUtils.begin(pos, ctx);
            ctx.drawString(mc.font, ownerName,
                -mc.font.width(ownerName) / 2, 0,
                nameColor.getValue().getRGB(), shadow.getValue());
            NametagUtils.end(ctx);
        }
    }

    private String getOwnerName(UUID uuid, Entity entity) {
        if (ownerCache.containsKey(uuid)) return ownerCache.get(uuid);

        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                if (player.getUUID().equals(uuid)) {
                    String name = player.getName().getString();
                    ownerCache.put(uuid, name);
                    return name;
                }
            }
        }

        return "Unknown";
    }
}

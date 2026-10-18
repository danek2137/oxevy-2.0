package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.impl.render.RenderBlockOutlineEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.Color;

public class CustomBlockOutlineModule extends Module {

    public final Setting<Boolean> rainbow = bool("Rainbow", false);
    public final Setting<Color> color = color("Color", 255, 255, 255, 255);
    public final Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.1f, 8.0f);
    public final Setting<Boolean> filled = bool("Filled", false);
    public final Setting<Float> fillOpacity = num("FillOpacity", 0.25f, 0.05f, 1.0f);
    public final Setting<Boolean> throughWalls = bool("ThroughWalls", true);
    public final Setting<Boolean> hideVanilla = bool("HideVanilla", true);

    private long lastColorUpdate;
    private Color rainbowColor = new Color(255, 255, 255, 255);

    public CustomBlockOutlineModule() {
        super("CustomBlockOutline", "Replaces the vanilla block outline with a custom one", Category.RENDER);
        fillOpacity.setVisibility(v -> filled.getValue());
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        if (!(mc.hitResult instanceof BlockHitResult result)) return;

        VoxelShape shape = mc.level.getBlockState(result.getBlockPos()).getShape(mc.level, result.getBlockPos());
        if (shape.isEmpty()) return;

        AABB box = shape.bounds().move(result.getBlockPos());
        Color c = getColor();
        if (filled.getValue()) {
            Color fillColor = new Color(c.getRed(), c.getGreen(), c.getBlue(),
                    (int) (c.getAlpha() * fillOpacity.getValue()));
            RenderUtil.drawBoxFilled(event.getMatrix(), box, fillColor);
        }
        RenderUtil.drawBox(event.getMatrix(), box, c, lineWidth.getValue(), throughWalls.getValue());
    }

    @Subscribe
    public void onRenderBlockOutline(RenderBlockOutlineEvent event) {
        if (hideVanilla.getValue()) {
            event.cancel();
        }
    }

    private Color getColor() {
        if (!rainbow.getValue()) return color.getValue();

        long now = System.currentTimeMillis();
        if (now - lastColorUpdate > 50) {
            lastColorUpdate = now;
            float hue = (now % 4000) / 4000.0f;
            int rgb = Color.HSBtoRGB(hue, 0.75f, 1.0f);
            rainbowColor = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
                    color.getValue().getAlpha());
        }
        return rainbowColor;
    }
}

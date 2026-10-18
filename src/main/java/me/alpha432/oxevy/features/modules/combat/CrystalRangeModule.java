package me.alpha432.oxevy.features.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.ColorUtil;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

public class CrystalRangeModule extends Module {

    public Setting<Boolean> showRadius = bool("BlastRadius", true);
    public Setting<Float> blastRadius = num("Radius", 6.0f, 1.0f, 12.0f);
    public Setting<Boolean> showRing = bool("Ring", true);
    public Setting<Float> ringRadius = num("RingRadius", 1.5f, 0.5f, 5.0f);
    public Setting<Boolean> showDome = bool("Dome", false);
    public Setting<Color> color = color("Color", 255, 50, 50, 180);
    public Setting<Boolean> rainbow = bool("Rainbow", false);
    public Setting<Float> speed = num("Speed", 1.0f, 0.1f, 5.0f);
    public Setting<Integer> segments = num("Segments", 32, 8, 64);
    public Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.5f, 4.0f);
    public Setting<Boolean> throughWalls = bool("ThroughWalls", true);

    public CrystalRangeModule() {
        super("CrystalRange", "Draws blast radius rings around End Crystals", Category.COMBAT);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null) return;

        long time = System.nanoTime();
        float t = (float) (time * 0.000000001 * speed.getValue());

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;

            double x = Mth.lerp(event.getDelta(), crystal.xo, crystal.getX());
            double y = Mth.lerp(event.getDelta(), crystal.yo, crystal.getY());
            double z = Mth.lerp(event.getDelta(), crystal.zo, crystal.getZ());
            Vec3 pos = new Vec3(x, y, z);

            int c;
            if (rainbow.getValue()) {
                float hue = (float) ((t * 0.3 + x * 0.1) % 1.0);
                c = Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0x00FFFFFF | ((color.getValue().getAlpha() & 0xFF) << 24);
            } else {
                c = ColorUtil.toRGBA(color.getValue());
            }

            int seg = segments.getValue();
            PoseStack stack = event.getMatrix();

            if (showRadius.getValue()) {
                RenderUtil.drawCircle(stack, pos, blastRadius.getValue(), seg, color.getValue(), lineWidth.getValue(), throughWalls.getValue());
            }

            if (showRing.getValue()) {
                float rr = ringRadius.getValue();
                float ringPulse = (float) (Math.sin(t * 2.0) * rr * 0.15);
                RenderUtil.drawBillboardRing(stack, pos, rr + ringPulse, rr + ringPulse + 0.03f, seg, c);
            }

            if (showDome.getValue()) {
                drawDome(stack, pos, blastRadius.getValue(), seg, c, throughWalls.getValue());
            }
        }
    }

    private void drawDome(PoseStack stack, Vec3 center, float radius, int seg, int colorInt, boolean throughWalls) {
        Vec3 camera = RenderUtil.getCameraPos();

        int rings = 6;
        Color c = new Color(colorInt, true);
        for (int ring = 1; ring <= rings; ring++) {
            float phi = (float) (Math.PI * 0.5 * ring / rings);
            float r = (float) Math.cos(phi) * radius;
            float h = (float) Math.sin(phi) * radius;
            Vec3 ringCenter = new Vec3(center.x, center.y + h, center.z);
            RenderUtil.drawCircle(stack, ringCenter, r, seg, c, lineWidth.getValue(), throughWalls);
        }
    }
}

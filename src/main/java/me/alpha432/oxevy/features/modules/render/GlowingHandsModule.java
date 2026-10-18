package me.alpha432.oxevy.features.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.ColorUtil;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

public class GlowingHandsModule extends Module {

    public Setting<Color> color = color("Color", 0, 200, 255, 200);
    public Setting<Boolean> rainbow = bool("Rainbow", false);
    public Setting<Float> speed = num("Speed", 1.5f, 0.1f, 5.0f);
    public Setting<Float> radius = num("Radius", 0.12f, 0.02f, 0.5f);
    public Setting<Integer> segments = num("Segments", 24, 8, 64);
    public Setting<Float> pulseAmount = num("Pulse", 0.04f, 0.0f, 0.2f);
    public Setting<Float> yOffset = num("YOffset", -0.4f, -1.0f, 1.0f);
    public Setting<Float> forward = num("Forward", 0.25f, 0.0f, 1.0f);
    public Setting<Boolean> throughWalls = bool("ThroughWalls", true);

    public GlowingHandsModule() {
        super("GlowingHands", "Draws pulsing aura rings around your hands", Category.RENDER);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        Vec3 eye = mc.player.getEyePosition(event.getDelta());
        Vec3 look = mc.player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 0.001) right = new Vec3(1, 0, 0);

        float fwd = forward.getValue();
        float yOff = yOffset.getValue();
        Vec3 mainHand = eye.add(right.scale(0.3)).add(new Vec3(0, yOff, 0)).add(look.scale(fwd));
        Vec3 offHand = eye.add(right.scale(-0.3)).add(new Vec3(0, yOff, 0)).add(look.scale(fwd));

        long time = System.nanoTime();
        float t = (float) (time * 0.000000001 * speed.getValue());
        float pulse = (float) (Math.sin(t * 3.0) * pulseAmount.getValue());
        float baseR = radius.getValue() + pulse;

        int c;
        if (rainbow.getValue()) {
            float hue = (float) ((t * 0.3) % 1.0);
            c = Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0x00FFFFFF | ((color.getValue().getAlpha() & 0xFF) << 24);
        } else {
            c = ColorUtil.toRGBA(color.getValue());
        }

        int seg = segments.getValue();
        PoseStack stack = event.getMatrix();

        drawAura(stack, mainHand, baseR, t, seg, c, throughWalls.getValue());
        drawAura(stack, offHand, baseR, t + 1.57f, seg, c, throughWalls.getValue());
    }

    private void drawAura(PoseStack stack, Vec3 center, float r, float time, int seg, int color, boolean throughWalls) {
        RenderUtil.drawBillboardRing(stack, center, r, r + 0.02f, seg, color);
        RenderUtil.drawBillboardRing(stack, center, r * 0.5f, r * 0.5f + 0.015f, seg, color);

        float phase = (float) (Math.sin(time * 2.0) * 0.5 + 0.5);
        int alpha = (color >> 24) & 0xFF;
        int faded = ColorUtil.toRGBA(
            (color >> 16) & 0xFF,
            (color >> 8) & 0xFF,
            color & 0xFF,
            (int) (alpha * phase * 0.4f)
        );
        RenderUtil.drawFilledRing(stack, center, 0.0f, r * 0.5f, seg, faded, throughWalls);
    }
}

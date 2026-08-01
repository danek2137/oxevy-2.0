package me.alpha432.oxevy.util.render.gl;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.alpha432.oxevy.util.render.Layers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class TargetCircleRenderer {

    public static void drawRing(PoseStack stack, LivingEntity target, float delta, float time, int color, boolean throughWalls) {
        Vec3 camera = getCameraPos();
        Vec3 targetPos = new Vec3(
            net.minecraft.util.Mth.lerp(delta, target.xo, target.getX()),
            net.minecraft.util.Mth.lerp(delta, target.yo, target.getY()),
            net.minecraft.util.Mth.lerp(delta, target.zo, target.getZ())
        );
        float width = target.getBbWidth();
        float height = target.getBbHeight();
        float cx = (float) (targetPos.x - camera.x);
        float cy = (float) (targetPos.y - camera.y);
        float cz = (float) (targetPos.z - camera.z);

        double yAnim = Math.abs(1.0 + Math.sin(time)) / 2.0 * height;
        double yAnim2 = Math.abs(1.0 + Math.sin(time - 0.45)) / 2.0 * height;

        int segs = 180;
        double[] cosArr = new double[segs + 1];
        double[] sinArr = new double[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double angle = i * 2.0 * Math.PI / segs;
            cosArr[i] = Math.cos(angle) * width;
            sinArr[i] = -Math.sin(angle) * width;
        }

        BufferBuilder bufferBuilder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();

        float yTopAbs = (float) (cy + yAnim);
        float yBotAbs = (float) (cy + yAnim2);

        for (int i = 0; i <= segs; i++) {
            float x = (float) (cx + cosArr[i]);
            float z = (float) (cz + sinArr[i]);

            int fromColor = multAlpha(color, (int) (0.6f * 255));
            int toColor = multAlpha(color, 0);

            bufferBuilder.addVertex(pose, x, yTopAbs, z).setColor(fromColor).setLineWidth(3f);
            bufferBuilder.addVertex(pose, x, yBotAbs, z).setColor(toColor).setLineWidth(3f);

            if (i < segs) {
                float xNext = (float) (cx + cosArr[i + 1]);
                float zNext = (float) (cz + sinArr[i + 1]);
                bufferBuilder.addVertex(pose, x, yTopAbs, z).setColor(color).setLineWidth(3f);
                bufferBuilder.addVertex(pose, xNext, yTopAbs, zNext).setColor(color).setLineWidth(3f);
            }
        }

        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    private static int multAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static Vec3 getCameraPos() {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().position();
    }
}

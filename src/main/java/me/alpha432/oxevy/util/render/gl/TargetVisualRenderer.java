package me.alpha432.oxevy.util.render.gl;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.alpha432.oxevy.util.render.Layers;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class TargetVisualRenderer {

    public static void drawGlowRing(PoseStack stack, LivingEntity target, float delta, float time, int color, boolean throughWalls) {
        Vec3 camera = getCameraPos();
        Vec3 targetPos = new Vec3(
            Mth.lerp(delta, target.xo, target.getX()),
            Mth.lerp(delta, target.yo, target.getY()),
            Mth.lerp(delta, target.zo, target.getZ())
        );
        float width = target.getBbWidth();
        float height = target.getBbHeight();
        float cx = (float) (targetPos.x - camera.x);
        float cy = (float) (targetPos.y - camera.y + height * 0.55);
        float cz = (float) (targetPos.z - camera.z);

        float pulse = 0.9f + 0.1f * (float) Math.sin(time * 2.0);
        double r = width * pulse;

        int segs = 120;
        double[] cosArr = new double[segs + 1];
        double[] sinArr = new double[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double angle = i * 2.0 * Math.PI / segs;
            cosArr[i] = Math.cos(angle) * r;
            sinArr[i] = -Math.sin(angle) * r;
        }

        BufferBuilder bufferBuilder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();

        // Draw 3 concentric rings with decreasing alpha for glow effect
        for (int layer = 2; layer >= 0; layer--) {
            float layerScale = 1.0f + layer * 0.2f;
            float alphaMul = 1.0f - layer * 0.3f;
            int layerColor = multAlpha(color, (int)((color >> 24 & 0xFF) * alphaMul));
            float lineW = 3f - layer * 1f;

            for (int i = 0; i < segs; i++) {
                float x1 = (float) (cx + cosArr[i] * layerScale);
                float z1 = (float) (cz + sinArr[i] * layerScale);
                float x2 = (float) (cx + cosArr[i + 1] * layerScale);
                float z2 = (float) (cz + sinArr[i + 1] * layerScale);

                double angleNorm = (double) i / segs;
                int rCol = (int) ((layerColor >> 16 & 0xFF) * (0.7 + 0.3 * Math.sin(angleNorm * Math.PI * 2 + time * 2)));
                int gCol = (int) ((layerColor >> 8 & 0xFF) * (0.7 + 0.3 * Math.sin(angleNorm * Math.PI * 2 + time * 2 + 2.094)));
                int bCol = (int) ((layerColor & 0xFF) * (0.7 + 0.3 * Math.sin(angleNorm * Math.PI * 2 + time * 2 + 4.188)));
                int segColor = (layerColor & 0xFF000000)
                    | (Math.min(255, Math.max(0, rCol)) << 16)
                    | (Math.min(255, Math.max(0, gCol)) << 8)
                    | Math.min(255, Math.max(0, bCol));

                bufferBuilder.addVertex(pose, x1, cy, z1).setColor(segColor).setLineWidth(lineW);
                bufferBuilder.addVertex(pose, x2, cy, z2).setColor(segColor).setLineWidth(lineW);
            }
        }

        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    public static void drawBeam(PoseStack stack, LivingEntity target, float delta, float time, int color, boolean throughWalls) {
        Vec3 camera = getCameraPos();
        Vec3 targetPos = new Vec3(
            Mth.lerp(delta, target.xo, target.getX()),
            Mth.lerp(delta, target.yo, target.getY()),
            Mth.lerp(delta, target.zo, target.getZ())
        );
        float width = target.getBbWidth();
        float height = target.getBbHeight();
        float cx = (float) (targetPos.x - camera.x);
        float cz = (float) (targetPos.z - camera.z);

        float bob = (float) (Math.sin(time * 3.0) * height * 0.3 + height * 0.5);
        float cy = (float) (targetPos.y - camera.y + bob);

        int segs = 60;
        double[] cosArr = new double[segs + 1];
        double[] sinArr = new double[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double angle = i * 2.0 * Math.PI / segs;
            cosArr[i] = Math.cos(angle) * width * 0.4;
            sinArr[i] = -Math.sin(angle) * width * 0.4;
        }

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        BufferBuilder bufferBuilder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();

        // Horizontal ring at current height
        for (int i = 0; i < segs; i++) {
            float x1 = (float) (cx + cosArr[i]);
            float z1 = (float) (cz + sinArr[i]);
            float x2 = (float) (cx + cosArr[i + 1]);
            float z2 = (float) (cz + sinArr[i + 1]);

            double angleNorm = (double) i / segs;
            int beamR = (int)(r * (0.5 + 0.5 * Math.sin(angleNorm * Math.PI * 2 + time * 4)));
            int beamG = (int)(g * (0.5 + 0.5 * Math.sin(angleNorm * Math.PI * 2 + time * 4 + 2.094)));
            int beamB = (int)(b * (0.5 + 0.5 * Math.sin(angleNorm * Math.PI * 2 + time * 4 + 4.188)));
            int segColor = (Math.min(255, Math.max(0, a)) << 24)
                | (Math.min(255, Math.max(0, beamR)) << 16)
                | (Math.min(255, Math.max(0, beamG)) << 8)
                | Math.min(255, Math.max(0, beamB));

            bufferBuilder.addVertex(pose, x1, cy, z1).setColor(segColor).setLineWidth(5f);
            bufferBuilder.addVertex(pose, x2, cy, z2).setColor(segColor).setLineWidth(5f);
        }

        // Vertical lines from beam down
        int fromAlpha = a;
        int toAlpha = 0;
        for (int i = 0; i < segs; i += 3) {
            float x = (float) (cx + cosArr[i]);
            float z = (float) (cz + sinArr[i]);
            float yBot = (float) (targetPos.y - camera.y);

            int topColor = (fromAlpha << 24) | (r << 16) | (g << 8) | b;
            int botColor = (toAlpha << 24) | (r << 16) | (g << 8) | b;

            bufferBuilder.addVertex(pose, x, cy, z).setColor(topColor).setLineWidth(2f);
            bufferBuilder.addVertex(pose, x, yBot, z).setColor(botColor).setLineWidth(2f);
        }

        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    public static void drawRotatingArc(PoseStack stack, LivingEntity target, float delta, float time, int color, boolean throughWalls) {
        Vec3 camera = getCameraPos();
        Vec3 targetPos = new Vec3(
            Mth.lerp(delta, target.xo, target.getX()),
            Mth.lerp(delta, target.yo, target.getY()),
            Mth.lerp(delta, target.zo, target.getZ())
        );
        float width = target.getBbWidth();
        float height = target.getBbHeight();
        float cx = (float) (targetPos.x - camera.x);
        float cy = (float) (targetPos.y - camera.y + height * 0.05);
        float cz = (float) (targetPos.z - camera.z);

        double baseR = width * 1.15;
        double arcLen = Math.PI * 0.55; // ~100 degrees
        double start = time * 2.2;
        int segs = 60;
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        BufferBuilder bufferBuilder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();

        // Glow layers
        for (int layer = 2; layer >= 0; layer--) {
            double layerR = baseR * (1.0 + layer * 0.12);
            float alphaMul = 1.0f - layer * 0.3f;
            float lineW = 4f - layer * 1.2f;
            int layerA = (int) (a * alphaMul);

            for (int i = 0; i < segs; i++) {
                double a1 = start + (double) i / segs * arcLen;
                double a2 = start + (double) (i + 1) / segs * arcLen;
                float x1 = (float) (cx + Math.cos(a1) * layerR);
                float z1 = (float) (cz - Math.sin(a1) * layerR);
                float x2 = (float) (cx + Math.cos(a2) * layerR);
                float z2 = (float) (cz - Math.sin(a2) * layerR);

                double norm = (double) i / segs;
                int segA = (int) (layerA * (0.6 + 0.4 * Math.sin(norm * Math.PI)));
                int segColor = (Math.min(255, Math.max(0, segA)) << 24)
                    | (Math.min(255, Math.max(0, r)) << 16)
                    | (Math.min(255, Math.max(0, g)) << 8)
                    | Math.min(255, Math.max(0, b));

                bufferBuilder.addVertex(pose, x1, cy, z1).setColor(segColor).setLineWidth(lineW);
                bufferBuilder.addVertex(pose, x2, cy, z2).setColor(segColor).setLineWidth(lineW);
            }
        }

        // Leading dot
        double headAngle = start + arcLen;
        float hx = (float) (cx + Math.cos(headAngle) * baseR);
        float hz = (float) (cz - Math.sin(headAngle) * baseR);
        int headColor = multAlpha(color, 255);
        bufferBuilder.addVertex(pose, hx - 0.15f, cy, hz).setColor(headColor).setLineWidth(6f);
        bufferBuilder.addVertex(pose, hx + 0.15f, cy, hz).setColor(headColor).setLineWidth(6f);
        bufferBuilder.addVertex(pose, hx, cy, hz - 0.15f).setColor(headColor).setLineWidth(6f);
        bufferBuilder.addVertex(pose, hx, cy, hz + 0.15f).setColor(headColor).setLineWidth(6f);

        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    private static int multAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static Vec3 getCameraPos() {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().position();
    }
}

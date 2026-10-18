package me.alpha432.oxevy.util.render;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import me.alpha432.oxevy.util.render.state.RectRenderState;
import me.alpha432.oxevy.util.render.state.RoundedRectRenderState;
import me.alpha432.oxevy.util.traits.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimized rendering utilities with batch rendering support.
 * Reduces GPU draw calls by batching similar operations.
 *
 * @author Oxevy Team
 * @version 2.1
 */
public class RenderUtil implements Util {

    // Per-frame cached values
    private static Vec3 cachedCameraPos = Vec3.ZERO;
    private static long lastCameraUpdateNanos = 0;
    private static final long CAMERA_CACHE_NS = 16_000_000L; // ~60fps

    /**
     * Get cached camera position (updated once per frame).
     * Avoids repeated calls to getMainCamera().position()
     */
    public static Vec3 getCameraPos() {
        long now = System.nanoTime();
        if (now - lastCameraUpdateNanos > CAMERA_CACHE_NS) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
                cachedCameraPos = mc.gameRenderer.getMainCamera().position();
            }
            lastCameraUpdateNanos = now;
        }
        return cachedCameraPos;
    }

    /**
     * Invalidates camera cache (call when camera changes drastically).
     */
    public static void invalidateCameraCache() {
        lastCameraUpdateNanos = 0;
    }
    public static Vec3 getTracerOrigin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return Vec3.ZERO;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 pos = camera.position();
        
        float yaw = camera.yRot();
        float pitch = camera.xRot();
        
        float radPerDeg = (float)Math.PI / 180f;
        float pi = (float)Math.PI;
        
        float adjustedYaw = -yaw * radPerDeg - pi;
        float cosYaw = (float)Math.cos(adjustedYaw);
        float sinYaw = (float)Math.sin(adjustedYaw);
        
        float adjustedPitch = -pitch * radPerDeg;
        float nCosPitch = -(float)Math.cos(adjustedPitch);
        float sinPitch = (float)Math.sin(adjustedPitch);
        
        Vec3 look = new Vec3(sinYaw * nCosPitch, sinPitch, cosYaw * nCosPitch);
        
        return new Vec3(pos.x + look.x * 10, pos.y + look.y * 10, pos.z + look.z * 10);
    }


    public static Identifier loadTexture(File file, String name) {
        try (FileInputStream fis = new FileInputStream(file)) {
            NativeImage image = NativeImage.read(fis);
            DynamicTexture texture = new DynamicTexture(() -> name, image);
            texture.upload();
            Identifier location = Identifier.fromNamespaceAndPath("oxevy", name.toLowerCase().replace(" ", "_"));
            Minecraft.getInstance().getTextureManager().register(location, texture);
            return location;
        } catch (Exception e) {
            me.alpha432.oxevy.Oxevy.LOGGER.error("Failed to load texture from {}", file.getPath(), e);
            return null;
        }
    }

    public static Identifier loadTextureFromResources(Identifier resourceId, String registryName) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(resourceId).orElse(null);
            if (resource == null) {
                me.alpha432.oxevy.Oxevy.LOGGER.error("Resource not found: {}", resourceId);
                return null;
            }
            NativeImage image = NativeImage.read(resource.open());
            DynamicTexture texture = new DynamicTexture(() -> registryName, image);
            texture.upload();
            Identifier location = Identifier.fromNamespaceAndPath("oxevy", registryName.toLowerCase().replace(" ", "_"));
            Minecraft.getInstance().getTextureManager().register(location, texture);
            return location;
        } catch (Exception e) {
            me.alpha432.oxevy.Oxevy.LOGGER.error("Failed to load texture from resources: {}", resourceId, e);
            return null;
        }
    }

    public static Identifier loadTextureFromUrl(String url, String registryName) {
        try {
            URLConnection conn = new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (InputStream is = conn.getInputStream()) {
                NativeImage image = NativeImage.read(is);
                DynamicTexture texture = new DynamicTexture(() -> registryName, image);
                texture.upload();
                Identifier location = Identifier.fromNamespaceAndPath("oxevy", registryName.toLowerCase().replace(" ", "_"));
                Minecraft.getInstance().getTextureManager().register(location, texture);
                return location;
            }
        } catch (Exception e) {
            me.alpha432.oxevy.Oxevy.LOGGER.error("Failed to load texture from {}", url, e);
            return null;
        }
    }

    // Helper methods for 3D rendering
    protected static void drawHorizontalLine(PoseStack matrices, float x1, float x2, float y, int color) {
        if (x2 < x1) {
            float i = x1;
            x1 = x2;
            x2 = i;
        }
        rectFilled(matrices, x1, y, x2 + 1, y + 1, color);
    }

    protected static void drawVerticalLine(PoseStack matrices, float x, float y1, float y2, int color) {
        if (y2 < y1) {
            float i = y1;
            y1 = y2;
            y2 = i;
        }
        rectFilled(matrices, x, y1 + 1, x + 1, y2, color);
    }

    protected static void drawHorizontalLine(PoseStack matrices, float x1, float x2, float y, int color, float width) {
        if (x2 < x1) {
            float i = x1;
            x1 = x2;
            x2 = i;
        }
        rectFilled(matrices, x1, y, x2 + width, y + width, color);
    }

    protected static void drawVerticalLine(PoseStack matrices, float x, float y1, float y2, int color, float width) {
        if (y2 < y1) {
            float i = y1;
            y1 = y2;
            y2 = i;
        }
        rectFilled(matrices, x, y1 + width, x + width, y2, color);
    }

    public static void rectFilled(PoseStack stack, float x1, float y1, float x2, float y2, int color) {
        float minX = Math.min(x1, x2);
        float maxX = Math.max(x1, x2);
        float minY = Math.min(y1, y2);
        float maxY = Math.max(y1, y2);
        
        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PoseStack.Pose pose = stack.last();
        
        float r = (float) ((color >> 16) & 0xFF) / 255.0f;
        float g = (float) ((color >> 8) & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;
        float a = (float) ((color >> 24) & 0xFF) / 255.0f;
        
        bufferBuilder.addVertex(pose, minX, maxY, 0.0f).setColor(r, g, b, a);
        bufferBuilder.addVertex(pose, maxX, maxY, 0.0f).setColor(r, g, b, a);
        bufferBuilder.addVertex(pose, maxX, minY, 0.0f).setColor(r, g, b, a);
        bufferBuilder.addVertex(pose, minX, minY, 0.0f).setColor(r, g, b, a);
        
        Layers.quads().draw(bufferBuilder.buildOrThrow());
    }

    public static void drawBoxFilled(PoseStack stack, AABB box, Color c) {
        drawBoxFilled(stack, box, c.getRGB());
    }

    public static void rect(GuiGraphics context, float x1, float y1, float x2, float y2, int color) {
        int ix1 = Math.round(x1);
        int iy1 = Math.round(y1);
        int ix2 = Math.round(x2);
        int iy2 = Math.round(y2);
        context.fill(ix1, iy1, ix2, iy2, color);
    }

    public static void rect(GuiGraphics context, float x1, float y1, float x2, float y2, int color, float width) {
        int w = Math.max(1, Math.round(width));
        context.fill(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y1) + w, color);
        context.fill(Math.round(x2) - w, Math.round(y1), Math.round(x2), Math.round(y2), color);
        context.fill(Math.round(x1), Math.round(y2) - w, Math.round(x2), Math.round(y2), color);
        context.fill(Math.round(x1), Math.round(y1), Math.round(x1) + w, Math.round(y2), color);
    }

    public static void roundRect(GuiGraphics context, float x, float y, float w, float h, float radius, int color) {
        roundRect(context, x, y, w, h, radius, radius, radius, radius, color);
    }

    public static void roundRect(GuiGraphics context, float x, float y, float w, float h,
            float tl, float tr, float br, float bl, int color) {
        if (w <= 0 || h <= 0) return;
        context.guiRenderState.submitGuiElement(new RoundedRectRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(context.pose()),
                x, y, w, h, tl, tr, br, bl, color,
                context.scissorStack.peek()
        ));
    }

    /**
     * rounded card with a visible 1px framing ring. Translucent fills on their
     * own vanish against dark scenes, so a faint outer ring keeps the surface's
     * silhouette legible everywhere (HUD cards, popups, gauges).
     */
    public static void outlinedRoundRect(GuiGraphics context, float x, float y, float w, float h,
            float radius, int fillColor, int ringColor) {
        roundRect(context, x - 1.0f, y - 1.0f, w + 2.0f, h + 2.0f, radius + 1.0f, ringColor);
        roundRect(context, x, y, w, h, radius, fillColor);
    }

    public static void horizontalGradient(GuiGraphics context, float x1, float y1, float x2, float y2, Color left, Color right) {
        int ix1 = Math.round(x1);
        int iy1 = Math.round(y1);
        int ix2 = Math.round(x2);
        int iy2 = Math.round(y2);

        int l = left.getRGB();
        int r = right.getRGB();
        gradient(context, ix1, iy1, ix2, iy2, l, l, r, r);
    }

    public static void verticalGradient(GuiGraphics context, float x1, float y1, float x2, float y2, Color top, Color bottom) {
        int ix1 = Math.round(x1);
        int iy1 = Math.round(y1);
        int ix2 = Math.round(x2);
        int iy2 = Math.round(y2);

        int t = top.getRGB();
        int b = bottom.getRGB();
        gradient(context, ix1, iy1, ix2, iy2, t, b, b, t);
    }

    public static void gradient(GuiGraphics graphics,
                                int x1, int y1, int x2, int y2,
                                int topLeft, int bottomLeft, int bottomRight, int topRight) {
        graphics.guiRenderState.submitGuiElement(new RectRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()),
                x1, y1, x2, y2,
                topLeft, bottomLeft, bottomRight, topRight,
                graphics.scissorStack.peek()
        ));
    }

    public static void rect(PoseStack stack, float x1, float y1, float x2, float y2, int color) {
        rectFilled(stack, x1, y1, x2, y2, color);
    }

    public static void rect(PoseStack stack, float x1, float y1, float x2, float y2, int color, float width) {
        drawHorizontalLine(stack, x1, x2, y1, color, width);
        drawVerticalLine(stack, x2, y1, y2, color, width);
        drawHorizontalLine(stack, x1, x2, y2, color, width);
        drawVerticalLine(stack, x1, y1, y2, color, width);
    }

    public static void drawBox(PoseStack stack, BlockPos pos, Color color, float width) {
        drawBox(stack, new AABB(pos), color, width, true);
    }

    public static void drawBox(PoseStack stack, AABB box, Color c, float lineWidth) {
        drawBox(stack, box, c, lineWidth, true);
    }

    public static void drawBox(PoseStack stack, AABB box, Color c, float lineWidth, boolean throughWalls) {
        drawBox(stack, box, c.getRGB(), lineWidth, throughWalls);
    }

    public static void drawBox(PoseStack stack, AABB box, int color, float lineWidth, boolean throughWalls) {
        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        writeBoxOutline(bufferBuilder, stack.last(), box, getCameraPos(), color, lineWidth);

        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    /** Writes a box's twelve edges, camera relative, into {@code bufferBuilder}. */
    private static void writeBoxOutline(BufferBuilder bufferBuilder, PoseStack.Pose pose, AABB box, Vec3 camera, int color, float lineWidth) {
        float minX = (float) (box.minX - camera.x);
        float minY = (float) (box.minY - camera.y);
        float minZ = (float) (box.minZ - camera.z);
        float maxX = (float) (box.maxX - camera.x);
        float maxY = (float) (box.maxY - camera.y);
        float maxZ = (float) (box.maxZ - camera.z);

        bufferBuilder.addVertex(pose, minX, minY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, minY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, minY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, maxY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, minY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, minY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, minY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, maxY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, maxY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, maxY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, maxY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, maxY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, maxY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, minY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, minY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, minY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, minY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, minY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, minX, maxY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, maxY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, minY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, maxY, maxZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, maxY, minZ).setColor(color).setLineWidth(lineWidth);
        bufferBuilder.addVertex(pose, maxX, maxY, maxZ).setColor(color).setLineWidth(lineWidth);
    }

    public static void drawOutlinedBoxes(PoseStack stack, List<AABB> boxes, int color, boolean throughWalls) {
        drawOutlinedBoxes(stack, boxes, color, throughWalls, 1.5f);
    }

    /**
     * Draws every box into a single buffer, so a list costs one draw call instead of
     * one per box. Geometry and ordering match calling {@link #drawBox} box by box.
     */
    public static void drawOutlinedBoxes(PoseStack stack, List<AABB> boxes, int color, boolean throughWalls, float lineWidth) {
        if (boxes.isEmpty()) return;

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();
        Vec3 camera = getCameraPos();

        for (AABB box : boxes) {
            writeBoxOutline(bufferBuilder, pose, box, camera, color, lineWidth);
        }

        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    /**
     * Fills every box in one buffer, so a list costs one draw call instead of one per box.
     * Fills always use {@link Layers#quads()}, i.e. they show through walls whatever
     * {@code throughWalls} says; the flag is kept so call sites read the same as the
     * outline variants.
     */
    public static void drawSolidBoxes(PoseStack stack, List<AABB> boxes, int color, boolean throughWalls) {
        if (boxes.isEmpty()) return;

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PoseStack.Pose pose = stack.last();
        Vec3 camera = getCameraPos();

        for (AABB box : boxes) {
            writeBoxFill(bufferBuilder, pose, box, camera, color);
        }

        Layers.quads().draw(bufferBuilder.buildOrThrow());
    }

    public static void drawBoxFilled(PoseStack stack, AABB box, int color) {
        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        writeBoxFill(bufferBuilder, stack.last(), box, getCameraPos(), color);

        Layers.quads().draw(bufferBuilder.buildOrThrow());
    }

    /** Writes a box's six faces, camera relative, into {@code bufferBuilder}. */
    private static void writeBoxFill(BufferBuilder bufferBuilder, PoseStack.Pose pose, AABB box, Vec3 camera, int color) {
        float minX = (float) (box.minX - camera.x);
        float minY = (float) (box.minY - camera.y);
        float minZ = (float) (box.minZ - camera.z);
        float maxX = (float) (box.maxX - camera.x);
        float maxY = (float) (box.maxY - camera.y);
        float maxZ = (float) (box.maxZ - camera.z);

        bufferBuilder.addVertex(pose, minX, minY, minZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, minY, minZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, minY, maxZ).setColor(color);
        bufferBuilder.addVertex(pose, minX, minY, maxZ).setColor(color);

        bufferBuilder.addVertex(pose, minX, maxY, minZ).setColor(color);
        bufferBuilder.addVertex(pose, minX, maxY, maxZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, maxY, maxZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, maxY, minZ).setColor(color);

        bufferBuilder.addVertex(pose, minX, minY, minZ).setColor(color);
        bufferBuilder.addVertex(pose, minX, maxY, minZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, maxY, minZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, minY, minZ).setColor(color);

        bufferBuilder.addVertex(pose, maxX, minY, minZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, maxY, minZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, maxY, maxZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, minY, maxZ).setColor(color);

        bufferBuilder.addVertex(pose, minX, minY, maxZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, minY, maxZ).setColor(color);
        bufferBuilder.addVertex(pose, maxX, maxY, maxZ).setColor(color);
        bufferBuilder.addVertex(pose, minX, maxY, maxZ).setColor(color);

        bufferBuilder.addVertex(pose, minX, minY, minZ).setColor(color);
        bufferBuilder.addVertex(pose, minX, minY, maxZ).setColor(color);
        bufferBuilder.addVertex(pose, minX, maxY, maxZ).setColor(color);
        bufferBuilder.addVertex(pose, minX, maxY, minZ).setColor(color);
    }

    public static void drawLine(PoseStack stack, Vec3 start, Vec3 end, Color color, float width, boolean throughWalls) {
        Vec3 camera = getCameraPos();
        float x1 = (float) (start.x - camera.x);
        float y1 = (float) (start.y - camera.y);
        float z1 = (float) (start.z - camera.z);
        float x2 = (float) (end.x - camera.x);
        float y2 = (float) (end.y - camera.y);
        float z2 = (float) (end.z - camera.z);

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();
        int c = color.getRGB();

        bufferBuilder.addVertex(pose, x1, y1, z1).setColor(c).setLineWidth(width);
        bufferBuilder.addVertex(pose, x2, y2, z2).setColor(c).setLineWidth(width);

        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    public static void drawSoupCircle(PoseStack stack, LivingEntity target, float delta, float animation, int color, boolean throughWalls) {
        Vec3 camera = getCameraPos();
        Vec3 targetPos = new Vec3(
            Mth.lerp(delta, target.xo, target.getX()),
            Mth.lerp(delta, target.yo, target.getY()),
            Mth.lerp(delta, target.zo, target.getZ())
        );
        float width = target.getBbWidth();
        float height = target.getBbHeight();
        float cx = (float) (targetPos.x - camera.x);
        float cy = (float) (targetPos.y - camera.y);
        float cz = (float) (targetPos.z - camera.z);

        double yAnim = Math.abs(1.0 + Math.sin(animation)) / 2.0 * height;
        double yAnim2 = Math.abs(1.0 + Math.sin(animation - 0.45)) / 2.0 * height;

        int segs = 90;
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

        for (int i = 0; i <= segs; i++) {
            float x = (float) (cx + cosArr[i]);
            float z = (float) (cz + sinArr[i]);
            float yTop = (float) (cy + yAnim);
            float yBot = (float) (cy + yAnim2);

            int fromColor = multAlpha(color, (int) (0.6f * 255));
            int toColor = multAlpha(color, 0);

            bufferBuilder.addVertex(pose, x, yTop, z).setColor(fromColor).setLineWidth(3f);
            bufferBuilder.addVertex(pose, x, yBot, z).setColor(toColor).setLineWidth(3f);

            if (i < segs) {
                float xNext = (float) (cx + cosArr[i + 1]);
                float zNext = (float) (cz + sinArr[i + 1]);
                bufferBuilder.addVertex(pose, x, yTop, z).setColor(color).setLineWidth(3f);
                bufferBuilder.addVertex(pose, xNext, yTop, zNext).setColor(color).setLineWidth(3f);
            }
        }

        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    private static int multAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    public static void drawCircle(PoseStack stack, Vec3 center, float radius, int segments, Color color, float width, boolean throughWalls) {
        Vec3 camera = getCameraPos();
        float cx = (float) (center.x - camera.x);
        float cy = (float) (center.y - camera.y);
        float cz = (float) (center.z - camera.z);

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();
        int c = color.getRGB();

        for (int i = 0; i < segments; i++) {
            float angle = (float) (i * 2 * Math.PI / segments);
            float nextAngle = (float) ((i + 1) * 2 * Math.PI / segments);
            float x1 = cx + (float) Math.cos(angle) * radius;
            float z1 = cz + (float) Math.sin(angle) * radius;
            float x2 = cx + (float) Math.cos(nextAngle) * radius;
            float z2 = cz + (float) Math.sin(nextAngle) * radius;

            bufferBuilder.addVertex(pose, x1, cy, z1).setColor(c).setLineWidth(width);
            bufferBuilder.addVertex(pose, x2, cy, z2).setColor(c).setLineWidth(width);
        }

        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    public static void drawFilledRing(PoseStack stack, Vec3 center, float innerRadius, float outerRadius, int segments, int color, boolean throughWalls) {
        Vec3 camera = getCameraPos();
        float cx = (float) (center.x - camera.x);
        float cy = (float) (center.y - camera.y);
        float cz = (float) (center.z - camera.z);

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PoseStack.Pose pose = stack.last();

        for (int i = 0; i < segments; i++) {
            double angle1 = i * 2 * Math.PI / segments;
            double angle2 = (i + 1) * 2 * Math.PI / segments;
            float x1i = cx + (float) (Math.cos(angle1) * innerRadius);
            float z1i = cz + (float) (Math.sin(angle1) * innerRadius);
            float x1o = cx + (float) (Math.cos(angle1) * outerRadius);
            float z1o = cz + (float) (Math.sin(angle1) * outerRadius);
            float x2i = cx + (float) (Math.cos(angle2) * innerRadius);
            float z2i = cz + (float) (Math.sin(angle2) * innerRadius);
            float x2o = cx + (float) (Math.cos(angle2) * outerRadius);
            float z2o = cz + (float) (Math.sin(angle2) * outerRadius);

            bufferBuilder.addVertex(pose, x1i, cy, z1i).setColor(color);
            bufferBuilder.addVertex(pose, x2i, cy, z2i).setColor(color);
            bufferBuilder.addVertex(pose, x2o, cy, z2o).setColor(color);
            bufferBuilder.addVertex(pose, x1o, cy, z1o).setColor(color);
        }

        (throughWalls ? Layers.quads() : Layers.quadsDepth()).draw(bufferBuilder.buildOrThrow());
    }

    public static void drawBillboardRing(PoseStack stack, Vec3 center, float innerRadius, float outerRadius, int segments, int color) {
        Vec3 camera = getCameraPos();
        float cx = (float) (center.x - camera.x);
        float cy = (float) (center.y - camera.y);
        float cz = (float) (center.z - camera.z);

        Vec3 lookDir = mc.gameRenderer.getMainCamera().position().subtract(center).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = lookDir.cross(up).normalize();
        Vec3 localUp = right.cross(lookDir).normalize();
        if (localUp.lengthSqr() < 0.001) {
            localUp = new Vec3(0, 0, 1);
            right = lookDir.cross(localUp).normalize();
            localUp = right.cross(lookDir).normalize();
        }

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PoseStack.Pose pose = stack.last();

        double rx = right.x, ry = right.y, rz = right.z;
        double ux = localUp.x, uy = localUp.y, uz = localUp.z;

        for (int i = 0; i < segments; i++) {
            double a1 = i * 2.0 * Math.PI / segments;
            double a2 = (i + 1) * 2.0 * Math.PI / segments;
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            double c2 = Math.cos(a2), s2 = Math.sin(a2);

            float x1i = (float) (cx + (rx * c1 + ux * s1) * innerRadius);
            float y1i = (float) (cy + (ry * c1 + uy * s1) * innerRadius);
            float z1i = (float) (cz + (rz * c1 + uz * s1) * innerRadius);
            float x1o = (float) (cx + (rx * c1 + ux * s1) * outerRadius);
            float y1o = (float) (cy + (ry * c1 + uy * s1) * outerRadius);
            float z1o = (float) (cz + (rz * c1 + uz * s1) * outerRadius);
            float x2i = (float) (cx + (rx * c2 + ux * s2) * innerRadius);
            float y2i = (float) (cy + (ry * c2 + uy * s2) * innerRadius);
            float z2i = (float) (cz + (rz * c2 + uz * s2) * innerRadius);
            float x2o = (float) (cx + (rx * c2 + ux * s2) * outerRadius);
            float y2o = (float) (cy + (ry * c2 + uy * s2) * outerRadius);
            float z2o = (float) (cz + (rz * c2 + uz * s2) * outerRadius);

            bufferBuilder.addVertex(pose, x1i, y1i, z1i).setColor(color);
            bufferBuilder.addVertex(pose, x2i, y2i, z2i).setColor(color);
            bufferBuilder.addVertex(pose, x2o, y2o, z2o).setColor(color);
            bufferBuilder.addVertex(pose, x1o, y1o, z1o).setColor(color);
        }

        Layers.quads().draw(bufferBuilder.buildOrThrow());
    }

    public static void drawCurvedLine(PoseStack stack, List<Vec3> points, int color, float lineWidth, boolean throughWalls) {
        if (points.size() < 2) return;
        Vec3 camera = getCameraPos();

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();

        for (int i = 1; i < points.size(); i++) {
            Vec3 prev = points.get(i - 1);
            Vec3 curr = points.get(i);
            float x1 = (float) (prev.x - camera.x);
            float y1 = (float) (prev.y - camera.y);
            float z1 = (float) (prev.z - camera.z);
            float x2 = (float) (curr.x - camera.x);
            float y2 = (float) (curr.y - camera.y);
            float z2 = (float) (curr.z - camera.z);

            bufferBuilder.addVertex(pose, x1, y1, z1).setColor(color).setLineWidth(lineWidth);
            bufferBuilder.addVertex(pose, x2, y2, z2).setColor(color).setLineWidth(lineWidth);
        }

        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    public static void drawTracers(PoseStack stack, float delta, List<Vec3> ends, int color, float lineWidth, boolean throughWalls) {
        if (ends.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 camera = getCameraPos();
        Vec3 start = getTracerOrigin();

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();

        for (Vec3 end : ends) {
            float sx = (float) (start.x - camera.x);
            float sy = (float) (start.y - camera.y);
            float sz = (float) (start.z - camera.z);
            float ex = (float) (end.x - camera.x);
            float ey = (float) (end.y - camera.y);
            float ez = (float) (end.z - camera.z);

            bufferBuilder.addVertex(pose, sx, sy, sz).setColor(color).setLineWidth(lineWidth);
            bufferBuilder.addVertex(pose, ex, ey, ez).setColor(color).setLineWidth(lineWidth);
        }
        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }

    public static void drawTracersToPoints(PoseStack stack, float delta, Vec3 start, List<Vec3> ends, List<Integer> colors, float lineWidth, boolean throughWalls) {
        if (ends.isEmpty()) return;
        Vec3 camera = getCameraPos();

        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH);
        PoseStack.Pose pose = stack.last();

        float sx = (float) (start.x - camera.x);
        float sy = (float) (start.y - camera.y);
        float sz = (float) (start.z - camera.z);

        for (int i = 0; i < ends.size(); i++) {
            Vec3 end = ends.get(i);
            int color = i < colors.size() ? colors.get(i) : 0x80FFFFFF;
            float ex = (float) (end.x - camera.x);
            float ey = (float) (end.y - camera.y);
            float ez = (float) (end.z - camera.z);

            bufferBuilder.addVertex(pose, sx, sy, sz).setColor(color).setLineWidth(lineWidth);
            bufferBuilder.addVertex(pose, ex, ey, ez).setColor(color).setLineWidth(lineWidth);
        }
        (throughWalls ? Layers.lines() : Layers.linesDepth()).draw(bufferBuilder.buildOrThrow());
    }
}

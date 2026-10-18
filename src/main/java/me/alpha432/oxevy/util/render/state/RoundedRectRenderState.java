package me.alpha432.oxevy.util.render.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fc;

/**
 * A real rounded rectangle submitted through the GUI render pipeline.
 *
 * The old approach stacked {@code fill} rectangles which produced stepped,
 * jagged corners. This tessellates the shape as a convex fan of quads whose
 * boundary follows smooth arcs at each corner. Every quad is degenerate over
 * the shape centre, so the fan never overlaps itself and translucent colours
 * stay crisp (no double-blend seams). The GUI pipeline uses QUADS and
 * POSITION_COLOR, matching vanilla {@code fill}.
 *
 * Each corner accepts its own radius (0 flattens that corner), so this also
 * renders pill shapes, top/bottom-only rounding and similar.
 */
public record RoundedRectRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
        float x, float y, float w, float h, float tl, float tr, float br, float bl, int color,
        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements GuiElementRenderState {

    public RoundedRectRenderState(RenderPipeline renderPipeline, TextureSetup textureSetup, Matrix3x2fc matrix3x2fc,
            float x, float y, float w, float h, float radius, int color, @Nullable ScreenRectangle screenRectangle) {
        this(renderPipeline, textureSetup, matrix3x2fc, x, y, w, h, radius, radius, radius, radius, color,
                screenRectangle, getBounds(x, y, w, h, matrix3x2fc, screenRectangle));
    }

    public RoundedRectRenderState(RenderPipeline renderPipeline, TextureSetup textureSetup, Matrix3x2fc matrix3x2fc,
            float x, float y, float w, float h, float tl, float tr, float br, float bl, int color,
            @Nullable ScreenRectangle screenRectangle) {
        this(renderPipeline, textureSetup, matrix3x2fc, x, y, w, h, tl, tr, br, bl, color,
                screenRectangle, getBounds(x, y, w, h, matrix3x2fc, screenRectangle));
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        Matrix3x2fc p = this.pose;
        if (w <= 0 || h <= 0) return;

        float maxR = Math.min(w / 2.0f, h / 2.0f);
        float rtl = clamp(tl, maxR);
        float rtr = clamp(tr, maxR);
        float rbr = clamp(br, maxR);
        float rbl = clamp(bl, maxR);

        if (rtl <= 0.0f && rtr <= 0.0f && rbr <= 0.0f && rbl <= 0.0f) {
            quad(vertexConsumer, p, x, y, x + w, y + h, color);
            return;
        }

        float[] bx = new float[96];
        float[] by = new float[96];
        int n = 0;

        // top edge (starts after the top-left arc)
        bx[n] = x + rtl;
        by[n] = y;
        n++;

        // top-right corner
        if (rtr > 0.0f) {
            arc(bx, by, n, x + w - rtr, y + rtr, rtr, -90.0f);
            n += arcSegmentsFor(rtr);
        } else {
            bx[n] = x + w;
            by[n] = y;
            n++;
        }

        // right edge
        bx[n] = x + w;
        by[n] = y + h - rbr;
        n++;

        // bottom-right corner
        if (rbr > 0.0f) {
            arc(bx, by, n, x + w - rbr, y + h - rbr, rbr, 0.0f);
            n += arcSegmentsFor(rbr);
        } else {
            bx[n] = x + w;
            by[n] = y + h;
            n++;
        }

        // bottom edge
        bx[n] = x + rbl;
        by[n] = y + h;
        n++;

        // bottom-left corner
        if (rbl > 0.0f) {
            arc(bx, by, n, x + rbl, y + h - rbl, rbl, 90.0f);
            n += arcSegmentsFor(rbl);
        } else {
            bx[n] = x;
            by[n] = y + h;
            n++;
        }

        // left edge
        bx[n] = x;
        by[n] = y + rtl;
        n++;

        // top-left corner (arc returns to the top edge start)
        if (rtl > 0.0f) {
            arc(bx, by, n, x + rtl, y + rtl, rtl, 180.0f);
            n += arcSegmentsFor(rtl);
        } else {
            bx[n] = x;
            by[n] = y;
            n++;
        }

        float cx = x + w / 2.0f;
        float cy = y + h / 2.0f;
        for (int i = 0; i < n; i++) {
            int next = i + 1 < n ? i + 1 : 0;
            emit(vertexConsumer, p, cx, cy, color);
            emit(vertexConsumer, p, bx[i], by[i], color);
            emit(vertexConsumer, p, bx[next], by[next], color);
            emit(vertexConsumer, p, cx, cy, color);
        }
    }

    private static float clamp(float r, float max) {
        return Math.max(0.0f, Math.min(r, max));
    }

    /** Returns how many segments the fitting {@link #arc(float[], float[], int, float, float, float, float)} writes. */
    private static int arcSegmentsFor(float radius) {
        int segments = (int) (radius * 1.2f);
        return Math.max(6, Math.min(20, segments));
    }

    /**
     * Appends a quarter arc (radii {@code [radius * 1.2]} clamped 6..20 segments).
     * Angles are in screen space (Y down), sweeping clockwise starting at startDeg.
     */
    private static void arc(float[] bx, float[] by, int index, float centX, float centY, float radius,
            float startDeg) {
        int segments = arcSegmentsFor(radius);
        for (int i = 1; i <= segments; i++) {
            double deg = Math.toRadians(startDeg + 90.0f * i / segments);
            bx[index + i - 1] = (float) (centX + Math.cos(deg) * radius);
            by[index + i - 1] = (float) (centY + Math.sin(deg) * radius);
        }
    }

    private static void quad(VertexConsumer vertexConsumer, Matrix3x2fc p,
            float x0, float y0, float x1, float y1, int color) {
        emit(vertexConsumer, p, x0, y0, color);
        emit(vertexConsumer, p, x0, y1, color);
        emit(vertexConsumer, p, x1, y1, color);
        emit(vertexConsumer, p, x1, y0, color);
    }

    private static void emit(VertexConsumer vertexConsumer, Matrix3x2fc p, float px, float py, int color) {
        vertexConsumer.addVertexWith2DPose(p, px, py).setColor(color);
    }

    @Nullable
    private static ScreenRectangle getBounds(float x, float y, float w, float h,
            Matrix3x2fc matrix3x2fc, @Nullable ScreenRectangle screenRectangle) {
        ScreenRectangle rect = new ScreenRectangle(
                Math.round(x), Math.round(y), Math.round(x + w) - Math.round(x), Math.round(y + h) - Math.round(y))
                .transformMaxBounds(matrix3x2fc);
        return screenRectangle != null ? screenRectangle.intersection(rect) : rect;
    }
}
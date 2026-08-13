package me.alpha432.oxevy.util.render;

import net.minecraft.client.renderer.RenderType;

import static me.alpha432.oxevy.util.render.Pipelines.GLOBAL_LINES_DEPTH_PIPELINE;
import static me.alpha432.oxevy.util.render.Pipelines.GLOBAL_LINES_PIPELINE;
import static me.alpha432.oxevy.util.render.Pipelines.GLOBAL_QUADS_DEPTH_PIPELINE;
import static me.alpha432.oxevy.util.render.Pipelines.GLOBAL_QUADS_PIPELINE;

public class Layers {
    private static final RenderType GLOBAL_QUADS = GLOBAL_QUADS_PIPELINE;
    private static final RenderType GLOBAL_QUADS_DEPTH = GLOBAL_QUADS_DEPTH_PIPELINE;
    private static final RenderType GLOBAL_LINES = GLOBAL_LINES_PIPELINE;
    private static final RenderType GLOBAL_LINES_DEPTH = GLOBAL_LINES_DEPTH_PIPELINE;

    public static RenderType quads() {
        return GLOBAL_QUADS;
    }

    /** Quads with depth test (hide behind walls). */
    public static RenderType quadsDepth() {
        return GLOBAL_QUADS_DEPTH;
    }

    public static RenderType lines() {
        return GLOBAL_LINES;
    }

    /** Lines with depth test (hide behind walls). */
    public static RenderType linesDepth() {
        return GLOBAL_LINES_DEPTH;
    }
}

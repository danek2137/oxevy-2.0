package me.alpha432.oxevy.util.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderStateShard.LineStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;

import java.util.OptionalDouble;

public class Pipelines {

    private static final CompositeState GLOBAL_QUADS_STATE = CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.NO_LIGHTMAP)
            .setOverlayState(RenderStateShard.NO_OVERLAY)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false);

    private static final CompositeState GLOBAL_QUADS_DEPTH_STATE = CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.NO_LIGHTMAP)
            .setOverlayState(RenderStateShard.NO_OVERLAY)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false);

    private static final CompositeState GLOBAL_LINES_STATE = CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new LineStateShard(OptionalDouble.empty()))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false);

    private static final CompositeState GLOBAL_LINES_DEPTH_STATE = CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new LineStateShard(OptionalDouble.empty()))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false);

    public static final RenderType GLOBAL_QUADS_PIPELINE = RenderType.create("global_fill",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, GLOBAL_QUADS_STATE);

    public static final RenderType GLOBAL_QUADS_DEPTH_PIPELINE = RenderType.create("global_fill_depth",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, GLOBAL_QUADS_DEPTH_STATE);

    public static final RenderType GLOBAL_LINES_PIPELINE = RenderType.create("global_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, GLOBAL_LINES_STATE);

    public static final RenderType GLOBAL_LINES_DEPTH_PIPELINE = RenderType.create("global_lines_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, GLOBAL_LINES_DEPTH_STATE);
}

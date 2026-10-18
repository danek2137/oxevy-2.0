package me.alpha432.oxevy.util.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import me.alpha432.oxevy.Oxevy;
import net.minecraft.client.Minecraft;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

public class ShaderUtil {
    private static final Minecraft mc = Minecraft.getInstance();

    private static ShaderProgram chromaShader;
    private static ShaderProgram solidShader;
    private static ShaderProgram glowShader;
    private static ShaderProgram smokeShader;
    private static ShaderProgram stripesShader;
    private static ShaderProgram snowShader;
    private static ShaderProgram copyShader;
    private static ShaderProgram skyShader;
    private static boolean initialized = false;

    private static int glErrorLogBudget = 20;

    private static int sceneTex = -1;
    private static int outTex = -1;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private static int quadVao = -1;
    private static int quadVbo = -1;

    public static void initShadersIfNeeded() {
        if (initialized) return;
        try {
            chromaShader = new ShaderProgram("oxevy", "hand/chroma");
            solidShader = new ShaderProgram("oxevy", "hand/solid");
            glowShader = new ShaderProgram("oxevy", "hand/glow");
            smokeShader = new ShaderProgram("oxevy", "hand/smoke");
            stripesShader = new ShaderProgram("oxevy", "hand/stripes");
            snowShader = new ShaderProgram("oxevy", "hand/snow");
            try { skyShader = new ShaderProgram("oxevy", "sky/sky"); } catch (Exception e) { Oxevy.LOGGER.warn("[SkyShader] failed to compile, falling back to flat color", e); skyShader = null; }
            copyShader = new ShaderProgram(
                "#version 330 core\n" +
                "layout(location = 0) in vec3 Position;\n" +
                "out vec2 uv;\n" +
                "void main() { gl_Position = vec4(Position, 1.0); uv = (Position.xy + 1.0) / 2.0; }\n",
                "#version 330 core\n" +
                "in vec2 uv;\n" +
                "out vec4 outColor;\n" +
                "uniform sampler2D ColorTexture;\n" +
                "void main() { outColor = texture(ColorTexture, uv); }\n",
                true
            );
            initialized = true;
            Oxevy.LOGGER.info("[HandShader] Shaders compiled successfully");
        } catch (Exception e) {
            Oxevy.LOGGER.error("Failed to initialize hand shaders", e);
        }
    }

    private static void ensureQuad() {
        if (quadVao != -1) return;
        float[] quadVerts = {
            -1f, -1f, 0f,
             1f, -1f, 0f,
             1f,  1f, 0f,
            -1f, -1f, 0f,
             1f,  1f, 0f,
            -1f,  1f, 0f,
        };
        quadVao = GL30.glGenVertexArrays();
        quadVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(quadVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quadVerts, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static int getFramebufferWidth() {
        RenderTarget target = mc.getMainRenderTarget();
        if (target != null && target.width > 0) return target.width;
        return mc.getWindow().getWidth();
    }

    private static int getFramebufferHeight() {
        RenderTarget target = mc.getMainRenderTarget();
        if (target != null && target.height > 0) return target.height;
        return mc.getWindow().getHeight();
    }

    public static void ensureFramebuffers() {
        int width = getFramebufferWidth();
        int height = getFramebufferHeight();
        if (width <= 0 || height <= 0) return;
        if (sceneTex != -1 && lastWidth == width && lastHeight == height) return;
        destroyFramebuffers();

        sceneTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        outTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, outTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        lastWidth = width;
        lastHeight = height;
        Oxevy.LOGGER.info("[HandShader] Scene texture created: {}x{}", width, height);
    }

    public static void destroyFramebuffers() {
        if (sceneTex != -1) { GL11.glDeleteTextures(sceneTex); sceneTex = -1; }
        if (outTex != -1) { GL11.glDeleteTextures(outTex); outTex = -1; }
        lastWidth = -1;
        lastHeight = -1;
    }

    private static void logGlErrors(String stage) {
        if (glErrorLogBudget <= 0) return;
        int err;
        while ((err = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            Oxevy.LOGGER.warn("[HandShader] GL error 0x{} at {}", Integer.toHexString(err), stage);
            if (--glErrorLogBudget <= 0) break;
        }
    }

    public static void renderShaderEffect(ShaderProgram shader, Runnable uniformSetter) {
        if (sceneTex == -1 || outTex == -1 || shader == null || copyShader == null) return;

        int w = getFramebufferWidth();
        int h = getFramebufferHeight();
        if (w <= 0 || h <= 0) return;

        RenderTarget mainRT = mc.getMainRenderTarget();
        if (mainRT == null) return;

        GpuTexture colorTex = mainRT.getColorTexture();
        GpuTexture depthTex = mainRT.getDepthTexture();
        if (colorTex == null || depthTex == null) return;

        int colorGlId = ((GlTexture) colorTex).glId();
        int depthGlId = ((GlTexture) depthTex).glId();

        ensureQuad();

        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL30.GL_VIEWPORT, prevViewport);
        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) GlStateManager._disableScissorTest();

        int effectFbo = -1;
        int srcFbo = -1;
        try {
            srcFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, srcFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorGlId, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                Oxevy.LOGGER.warn("[HandShader] Source framebuffer incomplete, skipping frame");
                return;
            }

            effectFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, effectFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, outTex, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                Oxevy.LOGGER.warn("[HandShader] Effect framebuffer incomplete, skipping frame");
                return;
            }

            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(depthGlId);

            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(sceneTex);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            logGlErrors("scene copy");

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, effectFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, effectFbo);
            GL30.glViewport(0, 0, w, h);

            GlStateManager._disableDepthTest();
            GlStateManager._disableBlend();

            shader.bind();
            shader.setUniform1i("ColorTexture", 0);
            shader.setUniform1i("DepthTexture", 1);
            shader.setUniform2f("resolution", (float) w, (float) h);
            uniformSetter.run();

            GL30.glBindVertexArray(quadVao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            shader.unbind();
            logGlErrors("effect pass");

            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(outTex);

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, srcFbo);
            copyShader.bind();
            copyShader.setUniform1i("ColorTexture", 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            copyShader.unbind();
            GL30.glBindVertexArray(0);
            logGlErrors("write-back");

            GlStateManager._enableDepthTest();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            GL30.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(0);
            if (effectFbo != -1) GL30.glDeleteFramebuffers(effectFbo);
            if (srcFbo != -1) GL30.glDeleteFramebuffers(srcFbo);
            if (scissor) GlStateManager._enableScissorTest();
        }
    }

    public static float getGameTime() {
        return (float) System.nanoTime() / 1_000_000_000f;
    }

    public static boolean isInitialized() { return initialized; }

    public static ShaderProgram getSkyShader() { return skyShader; }

    public static void renderSkyShader(float time, int topColor, int horizonColor, int bottomColor, float starDensity, float starBrightness) {
        if (skyShader == null) return;
        ensureQuad();
        int w = getFramebufferWidth();
        int h = getFramebufferHeight();
        if (w <= 0 || h <= 0) return;

        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL30.GL_VIEWPORT, prevViewport);

        GlStateManager._disableDepthTest();
        GlStateManager._disableBlend();

        skyShader.bind();
        skyShader.setUniform1f("time", time);
        skyShader.setUniform2f("resolution", (float) w, (float) h);
        skyShader.setUniform3f("topColor",
            (topColor >> 16 & 0xFF) / 255.0f,
            (topColor >> 8 & 0xFF) / 255.0f,
            (topColor & 0xFF) / 255.0f);
        skyShader.setUniform3f("horizonColor",
            (horizonColor >> 16 & 0xFF) / 255.0f,
            (horizonColor >> 8 & 0xFF) / 255.0f,
            (horizonColor & 0xFF) / 255.0f);
        skyShader.setUniform3f("bottomColor",
            (bottomColor >> 16 & 0xFF) / 255.0f,
            (bottomColor >> 8 & 0xFF) / 255.0f,
            (bottomColor & 0xFF) / 255.0f);
        skyShader.setUniform1f("starDensity", starDensity);
        skyShader.setUniform1f("starBrightness", starBrightness);

        GL11.glViewport(0, 0, w, h);
        GL30.glBindVertexArray(quadVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        skyShader.unbind();

        GlStateManager._enableDepthTest();
        GL30.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
    }
    public static ShaderProgram getChromaShader() { return chromaShader; }
    public static ShaderProgram getSolidShader() { return solidShader; }
    public static ShaderProgram getGlowShader() { return glowShader; }
    public static ShaderProgram getSmokeShader() { return smokeShader; }
    public static ShaderProgram getStripesShader() { return stripesShader; }
    public static ShaderProgram getSnowShader() { return snowShader; }

    public static class ShaderProgram {
        private int programId;

        public ShaderProgram(String namespace, String name) {
            String vertexPath = "/assets/" + namespace + "/shaders/" + name + ".vsh";
            String fragmentPath = "/assets/" + namespace + "/shaders/" + name + ".fsh";

            int vertexId = createShader(vertexPath, GL20.GL_VERTEX_SHADER);
            int fragmentId = createShader(fragmentPath, GL20.GL_FRAGMENT_SHADER);
            link(vertexId, fragmentId, name);
        }

        public ShaderProgram(String vertexSource, String fragmentSource, boolean inline) {
            int vertexId = compileInline(vertexSource, GL20.GL_VERTEX_SHADER, "inline-vertex");
            int fragmentId = compileInline(fragmentSource, GL20.GL_FRAGMENT_SHADER, "inline-fragment");
            link(vertexId, fragmentId, "inline");
        }

        private void link(int vertexId, int fragmentId, String name) {
            this.programId = GL20.glCreateProgram();
            GL20.glAttachShader(this.programId, vertexId);
            GL20.glAttachShader(this.programId, fragmentId);
            GL20.glLinkProgram(this.programId);

            if (GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS) == 0) {
                String log = GL20.glGetProgramInfoLog(this.programId);
                GL20.glDeleteProgram(this.programId);
                GL20.glDeleteShader(vertexId);
                GL20.glDeleteShader(fragmentId);
                throw new RuntimeException("Failed to link shader program: " + name + "\n" + log);
            }
            GL20.glDeleteShader(vertexId);
            GL20.glDeleteShader(fragmentId);
        }

        private int compileInline(String source, int type, String name) {
            int shaderId = GL20.glCreateShader(type);
            GL20.glShaderSource(shaderId, source);
            GL20.glCompileShader(shaderId);
            if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
                String log = GL20.glGetShaderInfoLog(shaderId);
                GL20.glDeleteShader(shaderId);
                throw new RuntimeException("Failed to compile shader: " + name + "\n" + log);
            }
            return shaderId;
        }

        private int createShader(String path, int type) {
            try {
                String source = readShaderFile(path);
                int shaderId = GL20.glCreateShader(type);
                GL20.glShaderSource(shaderId, source);
                GL20.glCompileShader(shaderId);
                if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
                    String log = GL20.glGetShaderInfoLog(shaderId);
                    GL20.glDeleteShader(shaderId);
                    throw new RuntimeException("Failed to compile shader: " + path + "\n" + log);
                }
                return shaderId;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load shader: " + path, e);
            }
        }

        private String readShaderFile(String path) throws IOException {
            InputStream stream = ShaderProgram.class.getResourceAsStream(path);
            if (stream == null) throw new IOException("Shader file not found: " + path);
            StringBuilder source = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) source.append(line).append("\n");
            }
            return source.toString();
        }

        public void bind() { GL20.glUseProgram(this.programId); }
        public void unbind() { GL20.glUseProgram(0); }

        public void setUniform1i(String name, int value) {
            int loc = GL20.glGetUniformLocation(this.programId, name);
            if (loc != -1) GL20.glUniform1i(loc, value);
        }

        public void setUniform1f(String name, float value) {
            int loc = GL20.glGetUniformLocation(this.programId, name);
            if (loc != -1) GL20.glUniform1f(loc, value);
        }

        public void setUniform2f(String name, float x, float y) {
            int loc = GL20.glGetUniformLocation(this.programId, name);
            if (loc != -1) GL20.glUniform2f(loc, x, y);
        }

        public void setUniform2f(String name, Vector2f vec) {
            setUniform2f(name, vec.x, vec.y);
        }

        public void setUniform3f(String name, float x, float y, float z) {
            int loc = GL20.glGetUniformLocation(this.programId, name);
            if (loc != -1) GL20.glUniform3f(loc, x, y, z);
        }

        public void setUniform3f(String name, Color c) {
            setUniform3f(name, c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f);
        }

        public void setUniformBool(String name, boolean value) {
            setUniform1i(name, value ? 1 : 0);
        }

        public void delete() { GL20.glDeleteProgram(this.programId); }
    }
}

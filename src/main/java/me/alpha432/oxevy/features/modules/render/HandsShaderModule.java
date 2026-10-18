package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.ShaderUtil;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector2f;

import java.awt.Color;

public class HandsShaderModule extends Module {

    public enum ShaderType {
        CHROMA, SOLID, GLOW, SMOKE, STRIPES, SNOW
    }

    public Setting<ShaderType> shaderType = mode("Shader", ShaderType.CHROMA);

    public Setting<Float> chromaSpeed = num("ChromaSpeed", 1.0f, 0.1f, 5.0f);
    public Setting<Float> chromaSaturation = num("ChromaSaturation", 0.8f, 0.0f, 1.0f);
    public Setting<Float> chromaBrightness = num("ChromaBrightness", 0.9f, 0.0f, 1.0f);

    public Setting<Color> solidColor1 = color("Color1", 255, 0, 0, 200);
    public Setting<Color> solidColor2 = color("Color2", 0, 0, 255, 200);
    public Setting<Float> solidSpeed = num("SolidSpeed", 1.0f, 0.1f, 5.0f);
    public Setting<Float> solidAlpha = num("SolidAlpha", 0.85f, 0.0f, 1.0f);

    public Setting<Boolean> glowFill = bool("GlowFill", true);
    public Setting<Float> glowRadius = num("GlowRadius", 1.0f, 0.1f, 5.0f);
    public Setting<Float> glowPower = num("GlowPower", 1.5f, 0.1f, 5.0f);
    public Setting<Color> glowColor = color("GlowColor", 0, 200, 255, 255);
    public Setting<Float> glowDispersion = num("GlowDispersion", 5.0f, 1.0f, 20.0f);

    public Setting<Color> smokeColor = color("SmokeColor", 100, 100, 200, 255);
    public Setting<Float> smokeIntensity = num("SmokeIntensity", 0.5f, 0.1f, 2.0f);

    public Setting<Color> stripesColor1 = color("StripesColor1", 255, 255, 255, 255);
    public Setting<Color> stripesColor2 = color("StripesColor2", 0, 0, 0, 255);
    public Setting<Float> stripesWidth = num("StripesWidth", 0.1f, 0.02f, 0.5f);
    public Setting<Float> stripesSpeed = num("StripesSpeed", 1.0f, 0.1f, 5.0f);

    public Setting<Color> snowColor = color("SnowColor", 200, 220, 255, 255);
    public Setting<Float> snowIntensity = num("SnowIntensity", 0.8f, 0.1f, 2.0f);
    public Setting<Float> snowSpeed = num("SnowSpeed", 1.0f, 0.1f, 5.0f);

    public Setting<Float> maskFeather = num("MaskFeather", 0.08f, 0.01f, 0.3f);
    public Setting<Float> handDepth = num("HandDepth", 0.06f, 0.005f, 0.5f);

    public Setting<Float> maskX = num("MaskX", 0.72f, 0.0f, 1.0f);
    public Setting<Float> maskY = num("MaskY", 0.40f, 0.0f, 1.0f);
    public Setting<Float> maskWidth = num("MaskWidth", 0.30f, 0.05f, 0.8f);
    public Setting<Float> maskHeight = num("MaskHeight", 0.35f, 0.05f, 0.8f);

    public HandsShaderModule() {
        super("HandsShader [W.I.P]", "Applies custom shaders to first-person hands", Category.RENDER);
    }

    @Override
    public void onEnable() {
        ShaderUtil.initShadersIfNeeded();
        ShaderUtil.ensureFramebuffers();
    }

    @Override
    public void onTick() {
        if (!ShaderUtil.isInitialized()) {
            ShaderUtil.initShadersIfNeeded();
            ShaderUtil.ensureFramebuffers();
        }
    }

    @Override
    public void onDisable() {
        ShaderUtil.destroyFramebuffers();
    }

    public void renderEffect() {
        if (!ShaderUtil.isInitialized()) {
            ShaderUtil.initShadersIfNeeded();
            ShaderUtil.ensureFramebuffers();
            if (!ShaderUtil.isInitialized()) return;
        }
        ShaderUtil.ensureFramebuffers();

        float time = ShaderUtil.getGameTime();

        RenderTarget mainTarget = mc.getMainRenderTarget();
        if (mainTarget == null) return;
        Vector2f resolution = new Vector2f(mainTarget.width, mainTarget.height);

        float cx = maskX.getValue();
        float cy = maskY.getValue();
        float hw = maskWidth.getValue() * 0.5f;
        float hh = maskHeight.getValue() * 0.5f;

        LocalPlayer player = mc.player;
        if (player != null) {
            HumanoidArm mainArm = player.getMainArm();
            boolean isOffHand = (mainArm == HumanoidArm.LEFT);
            if (isOffHand) cx = 1.0f - cx;

            float pitch = player.getXRot();
            cy -= (float) Math.toRadians(pitch) * 0.08f;
        }

        Vector2f handMin = new Vector2f(cx - hw, cy - hh);
        Vector2f handMax = new Vector2f(cx + hw, cy + hh);

        float feather = maskFeather.getValue();

        ShaderUtil.ShaderProgram shader;
        switch (shaderType.getValue()) {
            case CHROMA -> shader = ShaderUtil.getChromaShader();
            case SOLID -> shader = ShaderUtil.getSolidShader();
            case GLOW -> shader = ShaderUtil.getGlowShader();
            case SMOKE -> shader = ShaderUtil.getSmokeShader();
            case STRIPES -> shader = ShaderUtil.getStripesShader();
            case SNOW -> shader = ShaderUtil.getSnowShader();
            default -> shader = ShaderUtil.getChromaShader();
        }

        ShaderUtil.renderShaderEffect(shader, () -> {
            shader.setUniform2f("handMin", handMin);
            shader.setUniform2f("handMax", handMax);
            shader.setUniform1f("maskFeather", feather);
            shader.setUniform1f("handDepthThreshold", handDepth.getValue());

            switch (shaderType.getValue()) {
                case CHROMA -> {
                    shader.setUniform1f("time", time);
                    shader.setUniform1f("chromaSpeed", chromaSpeed.getValue());
                    shader.setUniform1f("chromaSaturation", chromaSaturation.getValue());
                    shader.setUniform1f("chromaBrightness", chromaBrightness.getValue());
                }
                case SOLID -> {
                    shader.setUniform1f("time", time * solidSpeed.getValue());
                    shader.setUniform3f("customColor1", solidColor1.getValue());
                    shader.setUniform3f("customColor2", solidColor2.getValue());
                    shader.setUniform1f("effectAlpha", solidAlpha.getValue());
                }
                case GLOW -> {
                    shader.setUniform1f("time", time);
                    shader.setUniform2f("resolution", resolution);
                    shader.setUniformBool("fillGlow", glowFill.getValue());
                    shader.setUniform1f("glowRadius", glowRadius.getValue());
                    shader.setUniform1f("glowPower", glowPower.getValue());
                    shader.setUniform1f("glowDispersion", glowDispersion.getValue());
                    shader.setUniform3f("glowColor", glowColor.getValue());
                }
                case SMOKE -> {
                    shader.setUniform1f("time", time);
                    shader.setUniform2f("resolution", resolution);
                    shader.setUniform3f("smokeColor", smokeColor.getValue());
                    shader.setUniform1f("smokeIntensity", smokeIntensity.getValue());
                }
                case STRIPES -> {
                    shader.setUniform1f("time", time);
                    shader.setUniform2f("resolution", resolution);
                    shader.setUniform3f("stripesColor1", stripesColor1.getValue());
                    shader.setUniform3f("stripesColor2", stripesColor2.getValue());
                    shader.setUniform1f("stripesWidth", stripesWidth.getValue());
                    shader.setUniform1f("stripesSpeed", stripesSpeed.getValue());
                }
                case SNOW -> {
                    shader.setUniform1f("time", time);
                    shader.setUniform2f("resolution", resolution);
                    shader.setUniform3f("snowColor", snowColor.getValue());
                    shader.setUniform1f("snowIntensity", snowIntensity.getValue());
                    shader.setUniform1f("snowSpeed", snowSpeed.getValue());
                }
            }
        });
    }
}

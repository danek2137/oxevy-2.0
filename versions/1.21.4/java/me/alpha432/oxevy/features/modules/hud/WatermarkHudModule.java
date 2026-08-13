package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class WatermarkHudModule extends HudModule {
    public Setting<Boolean> shadow = bool("Shadow", true);
    public Setting<Float> scale = num("Scale", 1.0f, 0.25f, 3.0f);

    private static final ResourceLocation WATERMARK_TEX = ResourceLocation.fromNamespaceAndPath("oxevy", "textures/watermark.png");
    private static final int TEX_W = 100;
    private static final int TEX_H = 50;
    private static final float PADDING = 4f;

    public WatermarkHudModule() {
        super("Watermark", "Displays watermark texture", TEX_W, TEX_H);
        pos.setVisibility(v -> false);
        enabled.setVisibility(v -> false);
        bind.setVisibility(v -> false);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void enable() {
        super.enable();
    }

    @Override
    public void disable() {
    }

    @Override
    public void toggle() {
    }

    @Override
    public float getX() {
        return PADDING;
    }

    @Override
    public float getY() {
        return PADDING;
    }

    @Override
    public boolean isHovering() {
        return false;
    }

    @Override
    public void drawContent(Render2DEvent e) {
        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();

        float s = scale.getValue();
        int w = (int) (TEX_W * s);
        int h = (int) (TEX_H * s);

        if (shadow.getValue()) {
            RenderUtil.rect(ctx, x + 2, y + 2, x + w + 2, y + h + 2, 0x33000000);
        }

        ctx.pose().pushPose();
        ctx.pose().translate(x, y,0.0F);
        ctx.pose().scale(s, s,1.0F);
        ctx.blit(RenderType::guiTextured, WATERMARK_TEX, 0, 0, 0f, 0f, TEX_W, TEX_H, TEX_W, TEX_H);
        ctx.pose().popPose();

        setWidth(w);
        setHeight(h);
    }
}

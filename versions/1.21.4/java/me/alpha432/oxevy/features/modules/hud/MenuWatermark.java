package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.modules.Module;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class MenuWatermark extends Module {
    private static final ResourceLocation WATERMARK_TEX = ResourceLocation.fromNamespaceAndPath("oxevy", "textures/watermark.png");

    public MenuWatermark() {
        super("MenuWatermark", "Shows watermark on main menu", Category.HUD);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!(mc.screen instanceof TitleScreen)) return;

        GuiGraphics ctx = event.getContext();
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();

        float scale = 0.35f;
        int w = (int) (800 * scale);
        int h = (int) (400 * scale);
        int x = (int) ((screenWidth - w) / 2);
        int y = (int) (screenHeight / 4 - h / 2);

        ctx.pose().pushPose();
        ctx.pose().translate(x, y, 0f);
        ctx.pose().scale(scale, scale, 1f);
        ctx.blit(RenderType::guiTextured, WATERMARK_TEX, 0, 0, 0f, 0f, 800, 400, 800, 400);
        ctx.pose().popPose();
    }
}

package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.network.OxevyUserManager;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class OxevyLogoModule extends Module {
    public final Setting<Boolean> showName = bool("ShowName", true);
    public final Setting<Boolean> oxevyOnly = bool("OxevyOnly", true);
    public final Setting<Float> yOffset = num("YOffset", 0.5f, 0.0f, 2.0f);
    public final Setting<Float> scale = num("Scale", 1.0f, 0.5f, 3.0f);

    public OxevyLogoModule() {
        super("OxevyLogo", "Shows Oxevy logo above other Oxevy users", Category.RENDER);
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) return;
        if (mc.level == null || mc.player == null) return;

        GuiGraphics ctx = event.getContext();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Matrix4f projMatrix = mc.gameRenderer.getProjectionMatrix(mc.getWindow().getGuiScaledWidth() / (float) mc.getWindow().getGuiScaledHeight());

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player)) continue;
            if (player == mc.player) continue;
            if (oxevyOnly.getValue() && !OxevyUserManager.isOxevyUser(player)) continue;

            double x = player.getX();
            double y = player.getY() + yOffset.getValue() + player.getBbHeight();
            double z = player.getZ();

            float[] screenPos = project(x, y, z, cameraPos, projMatrix);
            if (screenPos == null) continue;

            boolean isOxevy = OxevyUserManager.isOxevyUser(player);
            String text = showName.getValue() ? "Oxevy" : "\u2726";
            int color = isOxevy ? 0xFF00FF66 : 0xFF888888;

            int textWidth = mc.font.width(text);
            int scaledWidth = (int) (textWidth * scale.getValue());
            int lineHeight = (int) (mc.font.lineHeight * scale.getValue());

            int bgX = (int) (screenPos[0] - scaledWidth / 2f - 4);
            int bgY = (int) (screenPos[1] - lineHeight / 2f - 2);
            int bgW = scaledWidth + 8;
            int bgH = lineHeight + 4;

            RenderUtil.rect(ctx, bgX, bgY, bgX + bgW, bgY + bgH, 0x80000000);

            int textX = (int) (screenPos[0] - textWidth / 2f);
            int textY = (int) (screenPos[1] - mc.font.lineHeight / 2f);
            ctx.drawString(mc.font, text, textX, textY, color, false);
        }
    }

    private float[] project(double worldX, double worldY, double worldZ, Vec3 cameraPos, Matrix4f projMatrix) {
        float dx = (float) (worldX - cameraPos.x);
        float dy = (float) (worldY - cameraPos.y);
        float dz = (float) (worldZ - cameraPos.z);

        Vector4f pos = new Vector4f(dx, dy, dz, 1.0f);
        projMatrix.transform(pos);

        if (pos.w <= 0.0f) return null;

        float ndcX = pos.x / pos.w;
        float ndcY = pos.y / pos.w;

        if (ndcX < -1.0f || ndcX > 1.0f || ndcY < -1.0f || ndcY > 1.0f) return null;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        float screenX = (ndcX + 1.0f) * w / 2.0f;
        float screenY = (1.0f - ndcY) * h / 2.0f;

        return new float[] { screenX, screenY };
    }
}

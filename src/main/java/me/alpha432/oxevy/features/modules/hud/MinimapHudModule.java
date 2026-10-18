package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;

public class MinimapHudModule extends HudModule {

    public final Setting<Integer> size = num("Size", 120, 60, 220);
    public final Setting<Integer> chunkRadius = num("Chunks", 4, 1, 8);
    public final Setting<Boolean> showPlayers = bool("ShowPlayers", true);
    public final Setting<Boolean> showMobs = bool("ShowMobs", false);
    public final Setting<Boolean> showItems = bool("ShowItems", false);
    public final Setting<Boolean> showCardinals = bool("Cardinals", true);
    public final Setting<Float> slopeShading = num("SlopeShading", 1.0f, 0.0f, 2.0f);

    private static final Identifier MAP_TEX_ID = Identifier.fromNamespaceAndPath("oxevy", "minimap_terrain");
    private static final int COLOR_PLAYER = 0xFF00FF00;
    private static final int COLOR_HOSTILE = 0xFFFF4444;
    private static final int COLOR_PASSIVE = 0xFFFFFF44;
    private static final int COLOR_ITEM = 0xFFFFFFFF;
    private static final int COLOR_SELF = 0xFF00CCFF;

    private DynamicTexture terrainTexture;
    private int terrainRes = -1;
    private long lastUpdateTick = Long.MIN_VALUE;
    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;

    public MinimapHudModule() {
        super("Minimap", "Terrain minimap of your surroundings", 120, 120);
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (nullCheck()) return;

        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();
        float mapSize = size.getValue();
        int res = Math.min((int) mapSize, 160);

        ensureTexture(res);

        long tick = mc.level != null ? mc.level.getGameTime() : 0;
        boolean moved = Double.isNaN(lastX)
                || Math.abs(mc.player.getX() - lastX) > 1.5
                || Math.abs(mc.player.getZ() - lastZ) > 1.5;
        if (tick - lastUpdateTick >= 10 || moved) {
            lastUpdateTick = tick;
            lastX = mc.player.getX();
            lastZ = mc.player.getZ();
            updateTerrain(res);
        }

        setWidth(mapSize);
        setHeight(mapSize);

        ctx.fill((int) x - 1, (int) y - 1, (int) (x + mapSize + 1), (int) (y + mapSize + 1), 0xFF000000);
        ctx.blit(RenderPipelines.GUI_TEXTURED, MAP_TEX_ID,
                (int) x, (int) y, 0.0f, 0.0f, (int) mapSize, (int) mapSize, res, res);

        ctx.enableScissor((int) x, (int) y, (int) (x + mapSize), (int) (y + mapSize));

        float halfMap = mapSize / 2.0f;
        float centerX = x + halfMap;
        float centerY = y + halfMap;
        double spanBlocks = chunkRadius.getValue() * 16.0 * 2.0;
        double scale = mapSize / spanBlocks;

        AABB searchBox = new AABB(
                mc.player.getX() - spanBlocks / 2.0, mc.player.getY() - 32, mc.player.getZ() - spanBlocks / 2.0,
                mc.player.getX() + spanBlocks / 2.0, mc.player.getY() + 32, mc.player.getZ() + spanBlocks / 2.0
        );

        if (showPlayers.getValue()) {
            for (Entity entity : mc.level.getEntitiesOfClass(Player.class, searchBox)) {
                if (entity == mc.player) continue;
                drawDot(ctx, entity, centerX, centerY, scale, mapSize, COLOR_PLAYER);
            }
        }
        if (showMobs.getValue()) {
            for (Entity entity : mc.level.getEntitiesOfClass(Monster.class, searchBox)) {
                drawDot(ctx, entity, centerX, centerY, scale, mapSize, COLOR_HOSTILE);
            }
            for (Entity entity : mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, searchBox)) {
                drawDot(ctx, entity, centerX, centerY, scale, mapSize, COLOR_PASSIVE);
            }
        }
        if (showItems.getValue()) {
            for (Entity entity : mc.level.getEntitiesOfClass(ItemEntity.class, searchBox)) {
                drawDot(ctx, entity, centerX, centerY, scale, mapSize, COLOR_ITEM);
            }
        }

        drawSelfArrow(ctx, centerX, centerY);
        ctx.disableScissor();

        RenderUtil.rect(ctx, x - 1, y - 1, x + mapSize + 1, y + mapSize + 1, 0xFF555555, 1.0f);

        if (showCardinals.getValue()) {
            drawCardinals(ctx, x, y, mapSize);
        }
    }

    private void ensureTexture(int res) {
        if (terrainTexture == null || terrainRes != res) {
            if (terrainTexture != null) {
                try {
                    mc.getTextureManager().release(MAP_TEX_ID);
                } catch (Throwable ignored) {
                }
            }
            DynamicTexture fresh = new DynamicTexture(() -> "minimap_terrain", res, res, true);
            mc.getTextureManager().register(MAP_TEX_ID, fresh);
            terrainTexture = fresh;
            terrainRes = res;
            lastUpdateTick = Long.MIN_VALUE;
        }
    }

    private void updateTerrain(int res) {
        var image = terrainTexture.getPixels();
        if (image == null) return;

        double spanBlocks = chunkRadius.getValue() * 16.0 * 2.0;
        double step = spanBlocks / res;
        int baseX = (int) Math.floor(mc.player.getX() - spanBlocks / 2.0);
        int baseZ = (int) Math.floor(mc.player.getZ() - spanBlocks / 2.0);

        int[] heights = new int[res * res];
        for (int iz = 0; iz < res; iz++) {
            for (int ix = 0; ix < res; ix++) {
                int wx = baseX + (int) (ix * step);
                int wz = baseZ + (int) (iz * step);
                if (!mc.level.hasChunkAt(new BlockPos(wx >> 4, 0, wz >> 4))) {
                    heights[iz * res + ix] = Integer.MIN_VALUE;
                } else {
                    heights[iz * res + ix] = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                }
            }
        }

        float shadeStrength = slopeShading.getValue();

        for (int iz = 0; iz < res; iz++) {
            for (int ix = 0; ix < res; ix++) {
                int height = heights[iz * res + ix];
                int abgr = 0;
                if (height != Integer.MIN_VALUE && height > mc.level.getMinY()) {
                    int wx = baseX + (int) (ix * step);
                    int wz = baseZ + (int) (iz * step);
                    BlockPos pos = new BlockPos(wx, height - 1, wz);
                    MapColor mapColor = mc.level.getBlockState(pos).getMapColor(mc.level, pos);
                    if (mapColor != MapColor.NONE) {
                        int rgb = mapColor.col;

                        float factor = 1.0f;
                        if (shadeStrength > 0.01f) {
                            int right = ix + 1 < res ? heights[iz * res + ix + 1] : height;
                            int down = iz + 1 < res ? heights[(iz + 1) * res + ix] : height;
                            int dh = 0;
                            if (right != Integer.MIN_VALUE) dh += (height - right) >> 2;
                            if (down != Integer.MIN_VALUE) dh += (height - down) >> 2;
                            factor = Math.max(0.55f, Math.min(1.45f, 1.0f + dh * 0.08f * shadeStrength));
                        }

                        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * factor));
                        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * factor));
                        int b = Math.min(255, (int) ((rgb & 0xFF) * factor));
                        abgr = (0xFF << 24) | (b << 16) | (g << 8) | r;
                    }
                }
                image.setPixel(ix, iz, abgr);
            }
        }
        terrainTexture.upload();
    }

    private void drawDot(GuiGraphics ctx, Entity entity, float cx, float cy, double scale, float mapSize, int color) {
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();

        float dotX = cx + (float) (dx * scale);
        float dotY = cy + (float) (dz * scale);

        float half = mapSize / 2.0f;
        if (Math.abs(dotX - cx) > half - 3 || Math.abs(dotY - cy) > half - 3) return;

        ctx.fill((int) dotX - 1, (int) dotY - 1, (int) dotX + 1, (int) dotY + 1, color);
    }

    private void drawSelfArrow(GuiGraphics ctx, float cx, float cy) {
        float yaw = mc.player.getYRot();
        float yawRad = (float) Math.toRadians(yaw);
        float dirX = (float) -Math.sin(yawRad);
        float dirY = (float) -Math.cos(yawRad);

        float tipLen = 5.0f;
        float baseBack = 3.0f;
        float halfWidth = 3.0f;

        float tipX = cx + dirX * tipLen;
        float tipY = cy + dirY * tipLen;
        float leftX = cx - dirX * baseBack + dirY * halfWidth;
        float leftY = cy - dirY * baseBack - dirX * halfWidth;
        float rightX = cx - dirX * baseBack - dirY * halfWidth;
        float rightY = cy - dirY * baseBack + dirX * halfWidth;

        int minX = (int) Math.floor(Math.min(tipX, Math.min(leftX, rightX)));
        int maxX = (int) Math.ceil(Math.max(tipX, Math.max(leftX, rightX)));
        int minY = (int) Math.floor(Math.min(tipY, Math.min(leftY, rightY)));
        int maxY = (int) Math.ceil(Math.max(tipY, Math.max(leftY, rightY)));

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                if (pointInTriangle(px + 0.5f, py + 0.5f, tipX, tipY, leftX, leftY, rightX, rightY)) {
                    ctx.fill(px, py, px + 1, py + 1, COLOR_SELF);
                }
            }
        }
    }

    private boolean pointInTriangle(float px, float py, float x1, float y1, float x2, float y2, float x3, float y3) {
        float d1 = sign(px, py, x1, y1, x2, y2);
        float d2 = sign(px, py, x2, y2, x3, y3);
        float d3 = sign(px, py, x3, y3, x1, y1);
        boolean hasNeg = d1 < 0 || d2 < 0 || d3 < 0;
        boolean hasPos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(hasNeg && hasPos);
    }

    private float sign(float px, float py, float x1, float y1, float x2, float y2) {
        return (px - x2) * (y1 - y2) - (x1 - x2) * (py - y2);
    }

    private void drawCardinals(GuiGraphics ctx, float x, float y, float mapSize) {
        String[] labels = {"N", "S", "W", "E"};
        float[] xs = {mapSize / 2.0f, mapSize / 2.0f, 6.0f, mapSize - 6.0f};
        float[] ys = {7.0f, mapSize - 8.0f, mapSize / 2.0f, mapSize / 2.0f};

        for (int i = 0; i < 4; i++) {
            int textW = mc.font.width(labels[i]);
            ctx.drawString(mc.font, labels[i], (int) (x + xs[i] - textW / 2.0f), (int) (y + ys[i] - 4),
                    0xFFE0E0E0);
        }
    }

    @Override
    public void onDisable() {
        if (terrainTexture != null) {
            try {
                mc.getTextureManager().release(MAP_TEX_ID);
            } catch (Throwable ignored) {
            }
            terrainTexture = null;
            terrainRes = -1;
        }
    }
}

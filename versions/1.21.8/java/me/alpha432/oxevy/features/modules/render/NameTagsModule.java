package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.player.PlayerBadges;
import me.alpha432.oxevy.util.render.NametagUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NameTagsModule extends Module {
    public final Setting<Float> scale = num("Scale", 1.5f, 0.1f, 5.0f);
    public final Setting<Double> range = num("Range", 100.0, 16.0, 256.0);
    public final Setting<Boolean> unlimitedRange = bool("UnlimitedRange", true);
    public final Setting<Boolean> ignoreSelf = bool("IgnoreSelf", true);
    public final Setting<Boolean> ignoreFriends = bool("IgnoreFriends", false);
    public final Setting<Boolean> showHealth = bool("Health", true);
    public final Setting<Boolean> ping = bool("Ping", true);
    public final Setting<Boolean> distance = bool("Distance", false);
    public final Setting<Boolean> gamemode = bool("Gamemode", false);
    public final Setting<Boolean> armor = bool("Armor", true);
    public final Setting<Boolean> watermark = bool("Watermark", true);
    public final Setting<Boolean> shadow = bool("Shadow", true);
    public final Setting<Boolean> players = bool("Players", true);
    public final Setting<Boolean> mobs = bool("Mobs", false);

    private static final int BACKGROUND_COLOR = 0x90000000;
    private static final int ACCENT_COLOR = 0xFF00FF66;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int ARMOR_BG_COLOR = 0x80000000;

    private static final ResourceLocation OXEVY_ICON = ResourceLocation.fromNamespaceAndPath("oxevy", "textures/oxevy-icon.png");

    private final List<Entity> targets = new ArrayList<>();
    private final Vector3d pos = new Vector3d();

    public NameTagsModule() {
        super("NameTags", "Custom name tags above entities", Category.RENDER);
    }

    public float getScale() {
        return scale.getValue();
    }

    public boolean isUnlimitedRange() {
        return isEnabled() && unlimitedRange.getValue();
    }

    public boolean isSeeThrough() {
        return false;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        NametagUtils.onRender(event.getProjectionMatrix(), event.getPositionMatrix());

        targets.clear();
        double maxDist = unlimitedRange.getValue() ? Double.MAX_VALUE : range.getValue() * range.getValue();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player && ignoreSelf.getValue()) continue;
            if (!players.getValue() && entity.getType() == EntityType.PLAYER) continue;
            if (!mobs.getValue() && entity.getType() != EntityType.PLAYER) continue;
            if (entity.distanceToSqr(mc.player) > maxDist) continue;
            if (entity instanceof Player player && ignoreFriends.getValue() && Oxevy.friendManager.isFriend(player)) continue;
            targets.add(entity);
        }

        targets.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player)));
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        GuiGraphics ctx = event.getContext();

        for (Entity entity : targets) {
            pos.set(entity.getX(), entity.getY() + entity.getBbHeight() + 0.5, entity.getZ());
            if (!NametagUtils.to2D(pos, scale.getValue())) continue;

            if (entity instanceof Player player) {
                renderNametag(ctx, player);
            } else if (entity instanceof LivingEntity) {
                renderEntityTag(ctx, entity);
            }
        }
    }

    private void renderEntityTag(GuiGraphics ctx, Entity entity) {
        NametagUtils.begin(pos, ctx);

        String name = entity.getName().getString();
        int width = mc.font.width(name);
        int lineHeight = mc.font.lineHeight;

        int boxX = -width / 2 - 4;
        int boxY = -lineHeight - 2;
        int boxW = width + 8;
        int boxH = lineHeight + 4;

        ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, BACKGROUND_COLOR);
        ctx.fill(boxX, boxY, boxX + boxW, boxY + 1, ACCENT_COLOR);
        ctx.drawString(mc.font, name, -width / 2, -lineHeight, TEXT_COLOR, shadow.getValue());

        NametagUtils.end(ctx);
    }

    private void renderNametag(GuiGraphics ctx, Player player) {
        NametagUtils.begin(pos, ctx);

        String line = buildLine(player);
        int width = mc.font.width(line);
        int lineHeight = mc.font.lineHeight;
        boolean icon = hasWatermark(player);

        int iconSize = icon ? lineHeight : 0;
        int gap = icon ? 2 : 0;
        int boxW = width + iconSize + gap + 8;
        int boxX = -boxW / 2;
        int boxY = -lineHeight - 2;
        int boxH = lineHeight + 4;

        ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, BACKGROUND_COLOR);
        ctx.fill(boxX, boxY, boxX + boxW, boxY + 1, ACCENT_COLOR);

        if (icon) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, OXEVY_ICON, boxX + 4, boxY + 2, 0f, 0f, iconSize, iconSize, iconSize, iconSize);
        }

        ctx.drawString(mc.font, line, boxX + 4 + iconSize + gap, -lineHeight, TEXT_COLOR, shadow.getValue());

        if (armor.getValue()) {
            drawItems(ctx, player);
        }

        NametagUtils.end(ctx);
    }

    private String buildLine(Player player) {
        StringBuilder line = new StringBuilder();
        line.append(buildPrefix(player));
        line.append(player.getGameProfile().getName());
        line.append(buildSuffix(player));
        return line.toString();
    }

    private String buildPrefix(Player player) {
        StringBuilder prefix = new StringBuilder();

        if (gamemode.getValue()) {
            PlayerInfo info = getPlayerInfo(player);
            if (info != null && info.getGameMode() != null) {
                prefix.append("[").append(info.getGameMode().getName().charAt(0)).append("] ");
            }
        }

        String badge = PlayerBadges.getBadge(player);
        if (!badge.isEmpty()) {
            prefix.append(badge).append(" ");
        }

        return prefix.toString();
    }

    private boolean hasWatermark(Player player) {
        return watermark.getValue() && (player.getUUID().equals(mc.player.getUUID()) || Oxevy.oxevyUsersManager.isKnown(player));
    }

    private String buildSuffix(Player player) {
        StringBuilder suffix = new StringBuilder();

        if (showHealth.getValue()) {
            int health = Math.round(player.getHealth() + player.getAbsorptionAmount());
            suffix.append(" ").append(health);
        }

        if (ping.getValue()) {
            PlayerInfo info = getPlayerInfo(player);
            if (info != null) {
                suffix.append(" [").append(info.getLatency()).append("ms]");
            }
        }

        if (distance.getValue()) {
            double dist = Math.round(mc.player.distanceTo(player) * 10) / 10.0;
            suffix.append(" ").append(dist).append("m");
        }

        return suffix.toString();
    }

    private void drawItems(GuiGraphics ctx, Player player) {
        List<ItemStack> items = new ArrayList<>();
        items.add(player.getItemBySlot(EquipmentSlot.HEAD));
        items.add(player.getItemBySlot(EquipmentSlot.CHEST));
        items.add(player.getItemBySlot(EquipmentSlot.LEGS));
        items.add(player.getItemBySlot(EquipmentSlot.FEET));
        items.add(player.getMainHandItem());

        items.removeIf(ItemStack::isEmpty);
        if (items.isEmpty()) return;

        int iconSize = 16;
        int gap = 1;
        int totalWidth = items.size() * iconSize + (items.size() - 1) * gap;
        int x = -totalWidth / 2;
        int y = 6;

        ctx.fill(x - 1, y - 1, x + totalWidth + 1, y + iconSize + 1, ARMOR_BG_COLOR);
        for (ItemStack stack : items) {
            ctx.renderItem(stack, x, y);
            x += iconSize + gap;
        }
    }

    private PlayerInfo getPlayerInfo(Player player) {
        if (mc.getConnection() == null) return null;
        return mc.getConnection().getPlayerInfo(player.getUUID());
    }
}

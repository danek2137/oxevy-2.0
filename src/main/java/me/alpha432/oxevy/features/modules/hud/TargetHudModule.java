package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.util.render.AnimationUtil;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public class TargetHudModule extends HudModule {
    private Entity target = null;
    private float displayHealth;

    private static final float PAD = 10.0f;
    private static final float CARD_RADIUS = 8.0f;
    private static final float BAR_H = 4.0f;
    private static final float MIN_W = 120.0f;
    private static final int COLOR_BG = 0xAA0D0D0D;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TRACK = 0x33FFFFFF;

    public TargetHudModule() {
        super("TargetHUD", "Shows target info", 160, 60);
    }

    private int accent() {
        ClickGuiModule gui = ClickGuiModule.getInstance();
        if (gui != null && gui.color.getValue() != null) return gui.color.getValue().getRGB();
        return 0xFF7A2FF7;
    }

    private int healthColor(float percent) {
        if (percent > 0.75f) return 0xFF4ADE80;
        if (percent > 0.45f) return 0xFFFACC15;
        if (percent > 0.2f) return 0xFFFB923C;
        return 0xFFF87171;
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (nullCheck()) return;
        Entity newTarget = mc.crosshairPickEntity;
        if (newTarget != null && newTarget.isAlive() && newTarget != mc.player) target = newTarget;
        if (target == null || !target.isAlive() || mc.player.distanceTo(target) > 10) return;

        GuiGraphics ctx = e.getContext();
        float x = getX();
        float y = getY();

        LivingEntity living = target instanceof LivingEntity le ? le : null;
        String name = truncate(target.getName().getString(), 22);
        float hp = living != null ? living.getHealth() : 0.0f;
        float maxHp = living != null ? Math.max(1.0f, living.getMaxHealth()) : 20.0f;
        displayHealth = AnimationUtil.animate(displayHealth, hp, 0.15f, AnimationUtil.Easing.EASE_OUT);
        float percent = Math.max(0.0f, Math.min(1.0f, displayHealth / maxHp));
        String hpText = String.format(Locale.ROOT, "%.1f", hp);

        boolean hasArmor = false;
        if (target instanceof Player player) {
            for (int i = 3; i >= 0; i--) {
                if (!player.getItemBySlot(EquipmentSlot.values()[i + 2]).isEmpty()) {
                    hasArmor = true;
                    break;
                }
            }
        }

        float lineH = mc.font.lineHeight;
        float nameW = mc.font.width(name);
        float hpW = mc.font.width(hpText);
        float width = Math.max(nameW, hpW + 8.0f) + PAD * 2;
        if (hasArmor) width = Math.max(width, 16.0f * 4 + PAD * 2);
        width = Math.max(width, MIN_W);
        float row1Y = y + PAD;
        float barY = row1Y + lineH + 4;
        float height = PAD + lineH + 4 + BAR_H + (hasArmor ? 6.0f + 16.0f : 0.0f) + PAD;

        setWidth(width);
        setHeight(height);

        RenderUtil.roundRect(ctx, x, y, width, height, CARD_RADIUS, COLOR_BG);

        ctx.drawString(mc.font, name, (int) (x + PAD), (int) row1Y, COLOR_TEXT);
        ctx.drawString(mc.font, hpText, (int) (x + width - PAD - hpW), (int) row1Y, healthColor(percent));

        RenderUtil.roundRect(ctx, x + PAD, barY, width - PAD * 2, BAR_H, 2.0f, COLOR_TRACK);
        if (percent > 0.01f) {
            RenderUtil.roundRect(ctx, x + PAD, barY, (width - PAD * 2) * percent, BAR_H, 2.0f, healthColor(percent));
        }
        RenderUtil.roundRect(ctx, x + PAD, barY + BAR_H - 1.0f, (width - PAD * 2) * percent, 1.0f, 1.0f, accent());

        if (target instanceof Player player) {
            int armorX = (int) (x + PAD);
            int armorY = (int) (barY + BAR_H + 6);
            for (int i = 3; i >= 0; i--) {
                ItemStack stack = player.getItemBySlot(EquipmentSlot.values()[i + 2]);
                if (stack.isEmpty()) continue;
                ctx.renderItem(stack, armorX, armorY);
                armorX += 20;
            }
        }
    }

    private String truncate(String s, int maxChars) {
        if (s == null || s.isEmpty()) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars - 2) + "..";
    }
}

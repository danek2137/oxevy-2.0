package me.alpha432.oxevy.features.modules.client;

import me.alpha432.oxevy.event.impl.ClientEvent;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.gui.HudEditorScreen;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.modules.hud.HudModule;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class NotificationsModule extends HudModule {
    private static final ResourceLocation ADVANCEMENT_TOAST =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/toast/advancement.png");
    private static final int TOAST_WIDTH = 160;
    private static final int TOAST_HEIGHT = 32;
    private static final int TOAST_SPACING = 4;
    private static final long ANIMATION_MS = 180L;

    private static final Deque<NotificationEntry> NOTIFICATIONS = new ArrayDeque<>();
    private static NotificationsModule instance;

    public final Setting<Boolean> moduleToggle = bool("Module Toggle", true);
    public final Setting<Integer> duration = num("Duration", 3000, 1000, 10000);
    public final Setting<Integer> maxNotifications = num("Max", 5, 1, 10);

    public NotificationsModule() {
        super("Notifications", "Displays notifications on screen", TOAST_WIDTH, TOAST_HEIGHT);
        instance = this;
        pos.getValue().x = 0.82f;
        pos.getValue().y = 0.03f;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        NOTIFICATIONS.clear();
    }

    @Subscribe
    public void onClient(ClientEvent event) {
        if (!moduleToggle.getValue()
                || event.getType() != ClientEvent.Type.TOGGLE_MODULE
                || !(event.getFeature() instanceof Module module)
                || module instanceof ClickGuiModule) {
            return;
        }

        boolean enabled = module.isEnabled();
        Component title = Component.literal(enabled ? "Module enabled" : "Module disabled")
                .withStyle(enabled ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
        Component description = Component.literal(module.getDisplayName())
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);

        addNotification(title, description, iconFor(module), getConfiguredDuration());
    }

    public static void addNotification(String text, int color) {
        addNotification(
                Component.literal("Notification").withStyle(ChatFormatting.YELLOW),
                Component.literal(text).withStyle(colorToFormatting(color)),
                new ItemStack(Items.BELL),
                getConfiguredDuration()
        );
    }

    public static void addNotification(Component title, Component description, ItemStack icon, long durationMs) {
        pruneExpired(System.currentTimeMillis());
        int max = getConfiguredMaxNotifications();
        while (NOTIFICATIONS.size() >= max && !NOTIFICATIONS.isEmpty()) {
            NOTIFICATIONS.removeFirst();
        }

        NOTIFICATIONS.addLast(new NotificationEntry(title, description, icon.copy(), System.currentTimeMillis(), durationMs));
    }

    @Override
    public void drawContent(Render2DEvent e) {
        long now = System.currentTimeMillis();
        pruneExpired(now);

        GuiGraphics ctx = e.getContext();
        float baseX = getX();
        float baseY = getY();
        boolean showPreview = NOTIFICATIONS.isEmpty() && mc.screen instanceof HudEditorScreen;

        int rendered = 0;
        if (showPreview) {
            renderToast(ctx, baseX, baseY,
                    Component.literal("Module enabled").withStyle(ChatFormatting.YELLOW),
                    Component.literal("Notifications").withStyle(ChatFormatting.GREEN),
                    new ItemStack(Items.BELL),
                    1.0f);
            rendered = 1;
        } else {
            Iterator<NotificationEntry> iterator = NOTIFICATIONS.descendingIterator();
            while (iterator.hasNext()) {
                NotificationEntry entry = iterator.next();
                float drawY = baseY + rendered * (TOAST_HEIGHT + TOAST_SPACING);
                float drawX = baseX + animationOffset(now, entry);
                renderToast(ctx, drawX, drawY, entry.title, entry.description, entry.icon, 1.0f);
                rendered++;
            }
        }

        setWidth(TOAST_WIDTH);
        setHeight(rendered <= 0 ? TOAST_HEIGHT : rendered * TOAST_HEIGHT + Math.max(0, rendered - 1) * TOAST_SPACING);
    }

    private void renderToast(GuiGraphics ctx, float x, float y, Component title, Component description, ItemStack icon, float alpha) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        ctx.blit(ADVANCEMENT_TOAST, ix, iy, 0.0f, 0.0f, TOAST_WIDTH, TOAST_HEIGHT, TOAST_WIDTH, TOAST_HEIGHT);
        ctx.renderItem(icon, ix + 8, iy + 8);
        ctx.drawString(mc.font, title, ix + 30, iy + 7, applyAlpha(0xFFFFFFFF, alpha), false);
        ctx.drawString(mc.font, description, ix + 30, iy + 18, applyAlpha(0xFFFFFFFF, alpha), false);
    }

    private static float animationOffset(long now, NotificationEntry entry) {
        long age = now - entry.createdAt;
        long remaining = entry.durationMs - age;

        float enter = Math.min(1.0f, age / (float) ANIMATION_MS);
        float exit = remaining <= ANIMATION_MS ? Math.max(0.0f, remaining / (float) ANIMATION_MS) : 1.0f;
        float progress = Math.min(enter, exit);
        return (1.0f - progress) * 18.0f;
    }

    private static int applyAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static void pruneExpired(long now) {
        Iterator<NotificationEntry> iterator = NOTIFICATIONS.iterator();
        while (iterator.hasNext()) {
            NotificationEntry entry = iterator.next();
            if (now - entry.createdAt >= entry.durationMs) {
                iterator.remove();
            }
        }
    }

    private static long getConfiguredDuration() {
        return instance != null ? instance.duration.getValue() : 3000L;
    }

    private static int getConfiguredMaxNotifications() {
        return instance != null ? instance.maxNotifications.getValue() : 5;
    }

    private static ChatFormatting colorToFormatting(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (g >= r && g >= b) {
            return ChatFormatting.GREEN;
        }
        if (r >= g && r >= b) {
            return ChatFormatting.RED;
        }
        return ChatFormatting.WHITE;
    }

    private static ItemStack iconFor(Module module) {
        return switch (module.getCategory()) {
            case COMBAT -> new ItemStack(Items.DIAMOND_SWORD);
            case MOVEMENT -> new ItemStack(Items.FEATHER);
            case RENDER -> new ItemStack(Items.SPYGLASS);
            case PLAYER -> new ItemStack(Items.CRAFTING_TABLE);
            case CLIENT -> new ItemStack(Items.REDSTONE);
            case HUD -> new ItemStack(Items.ITEM_FRAME);
            case MISC -> new ItemStack(Items.CLOCK);
        };
    }

    private record NotificationEntry(Component title, Component description, ItemStack icon, long createdAt, long durationMs) {
    }
}

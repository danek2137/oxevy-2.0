package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.manager.ConfigManager;
import me.alpha432.oxevy.util.render.AnimationUtil;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.List;

public class ConfigsScreen extends net.minecraft.client.gui.screens.Screen {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final int WIDTH = 480;
    private static final int HEIGHT = 340;
    private static final int ROW_STEP = 28;
    private static final int ROW_H = 22;

    private float openAnimation = 0f;

    private List<String> profiles;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String message = "";
    private int messageTimer = 0;
    private boolean showCreateField = false;
    private EditBox createField;

    private String confirmProfile = null;

    public ConfigsScreen() {
        super(Component.literal("Config Manager"));
        refreshProfiles();
    }

    @Override
    protected void init() {
        createField = new EditBox(CustomFont.font(), width / 2 - 80, height / 2 + 10, 160, 18, Component.literal("Profile name"));
        createField.setMaxLength(32);
        createField.setVisible(false);
        createField.setValue("");
    }

    private void refreshProfiles() {
        profiles = Oxevy.configManager.listConfigs();
        int last = profiles.size() * ROW_STEP;
        maxScroll = Math.max(0, last - (HEIGHT - 90));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private int accent() {
        return ClickGuiModule.getInstance().color.getValue().getRGB();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        openAnimation = AnimationUtil.animate(openAnimation, 1.0f, 0.15f, AnimationUtil.Easing.EASE_OUT);

        int x = (context.guiWidth() - WIDTH) / 2;
        int y = (context.guiHeight() - HEIGHT) / 2;

        context.fill(0, 0, context.guiWidth(), context.guiHeight(), new Color(0, 0, 0, (int) (150 * openAnimation)).getRGB());

        RenderUtil.rect(context, x, y, x + WIDTH, y + HEIGHT, 0xFF121212);
        RenderUtil.rect(context, x, y, x + WIDTH, y + 2, accent());

        context.drawString(CustomFont.font(), "Config Manager", x + 15, y + 12, 0xFFAAAAAA);

        String cur = Oxevy.configManager.getCurrentConfig();
        String curText = "Current: " + cur;
        context.drawString(CustomFont.font(), curText, x + WIDTH - 15 - CustomFont.font().width(curText), y + 12, 0xFF55FF55);

        // Profile list
        int contentY = y + 35 - scrollOffset;
        String current = Oxevy.configManager.getCurrentConfig();
        for (String profile : profiles) {
            if (contentY >= y + 35 && contentY <= y + HEIGHT - 60) {
                boolean hovered = mouseX >= x + 15 && mouseX <= x + WIDTH - 15 && mouseY >= contentY && mouseY <= contentY + ROW_H;
                boolean isCurrent = profile.equals(current);

                int bgColor = isCurrent ? 0xFF2A2A3A : (hovered ? 0x1AFFFFFF : 0x12FFFFFF);
                RenderUtil.roundRect(context, x + 15, contentY, WIDTH - 30, ROW_H, 6f, bgColor);

                if (isCurrent) {
                    RenderUtil.roundRect(context, x + 15, contentY, 4, ROW_H, 2f, accent());
                }

                context.drawString(CustomFont.font(), profile, x + 27, contentY + 7, isCurrent ? 0xFFFFFFFF : 0xFFE6E6E6);

                // Delete button (not for default)
                if (!"default".equals(profile)) {
                    boolean delHover = mouseX >= x + WIDTH - 48 && mouseX <= x + WIDTH - 32 && mouseY >= contentY + 4 && mouseY <= contentY + 18;
                    RenderUtil.roundRect(context, x + WIDTH - 48, contentY + 4, 16, 14, 4f, delHover ? 0x55FF5555 : 0x22FFFFFF);
                    context.drawString(CustomFont.font(), "x", x + WIDTH - 43, contentY + 7, 0xFFFF5555);
                }
            }
            contentY += ROW_STEP;
        }

        // Bottom action bar
        int barY = y + HEIGHT - 34;
        drawButton(context, x + 15, barY, 80, 20, "Save", 0xFF55FF55, mouseX, mouseY);
        drawButton(context, x + 105, barY, 80, 20, "Load", 0xFF55CCFF, mouseX, mouseY);
        drawButton(context, x + 195, barY, 80, 20, "Create", 0xFFFFFF55, mouseX, mouseY);
        drawButton(context, x + WIDTH - 55, barY, 40, 20, "R", 0xFFAAAAAA, mouseX, mouseY);

        // Create field overlay
        if (showCreateField) {
            int fx = x + WIDTH / 2 - 120;
            int fy = y + HEIGHT / 2 - 60;
            int fw = 240;
            int fh = 120;
            RenderUtil.rect(context, fx, fy, fx + fw, fy + fh, 0xE0121212);
            RenderUtil.rect(context, fx, fy, fx + fw, fy + 2, accent());
            context.drawString(CustomFont.font(), "New Profile Name", fx + 15, fy + 12, 0xFFAAAAAA);

            createField.setX(fx + 15);
            createField.setY(fy + 30);
            createField.setWidth(fw - 30);
            createField.setVisible(true);
            createField.render(context, mouseX, mouseY, delta);

            drawButton(context, fx + 15, fy + 64, 100, 20, "Create", 0xFF55FF55, mouseX, mouseY);
            drawButton(context, fx + 125, fy + 64, 100, 20, "Cancel", 0xFFFF5555, mouseX, mouseY);
        } else {
            createField.visible = false;
        }

        // Confirmation dialog
        if (confirmProfile != null) {
            int fx = x + WIDTH / 2 - 120;
            int fy = y + HEIGHT / 2 - 45;
            int fw = 240;
            int fh = 90;
            RenderUtil.rect(context, fx, fy, fx + fw, fy + fh, 0xE0121212);
            RenderUtil.rect(context, fx, fy, fx + fw, fy + 2, 0xFFFF5555);
            context.drawString(CustomFont.font(), "Delete profile: " + confirmProfile + "?", fx + 15, fy + 14, 0xFFFF5555);
            drawButton(context, fx + 15, fy + 44, 100, 20, "Yes", 0xFFFF5555, mouseX, mouseY);
            drawButton(context, fx + 125, fy + 44, 100, 20, "Cancel", 0xFFAAAAAA, mouseX, mouseY);
        }

        if (messageTimer > 0) messageTimer--;
        if (!message.isEmpty() && messageTimer > 0) {
            context.drawString(CustomFont.font(), message, x + 15, y + HEIGHT - 52, 0xFF55FF55);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawButton(GuiGraphics context, int bx, int by, int bw, int bh, String label, int textColor, int mouseX, int mouseY) {
        boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        RenderUtil.roundRect(context, bx, by, bw, bh, 4f, hovered ? 0x33FFFFFF : 0x22FFFFFF);
        context.drawString(CustomFont.font(), label, bx + (bw - CustomFont.font().width(label)) / 2, by + 6, textColor);
    }

    private boolean inRect(double mouseX, double mouseY, int rx, int ry, int rw, int rh) {
        return mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        int x = (mc.getWindow().getGuiScaledWidth() - WIDTH) / 2;
        int y = (mc.getWindow().getGuiScaledHeight() - HEIGHT) / 2;

        if (button == 0) {
            // Confirmation dialog
            if (confirmProfile != null) {
                int fx = x + WIDTH / 2 - 120;
                int fy = y + HEIGHT / 2 - 45;
                if (inRect(mouseX, mouseY, fx + 15, fy + 44, 100, 20)) {
                    if (Oxevy.configManager.deleteProfile(confirmProfile)) {
                        setMessage("Deleted profile: " + confirmProfile);
                    } else {
                        setMessage("Failed to delete profile");
                    }
                    refreshProfiles();
                    confirmProfile = null;
                    return true;
                }
                if (inRect(mouseX, mouseY, fx + 125, fy + 44, 100, 20)) {
                    confirmProfile = null;
                    return true;
                }
                return true;
            }

            // Create field dialog
            if (showCreateField) {
                int fx = x + WIDTH / 2 - 120;
                int fy = y + HEIGHT / 2 - 60;
                if (inRect(mouseX, mouseY, fx + 15, fy + 64, 100, 20)) {
                    commitCreate();
                    return true;
                }
                if (inRect(mouseX, mouseY, fx + 125, fy + 64, 100, 20)) {
                    closeCreate();
                    return true;
                }
                if (inRect(mouseX, mouseY, fx + 15, fy + 30, 210, 18)) {
                    createField.mouseClicked(click, doubled);
                    createField.setFocused(true);
                    return true;
                }
                return true;
            }

            // Click on a profile row
            int contentY = y + 35 - scrollOffset;
            for (String profile : profiles) {
                boolean inRow = mouseX >= x + 15 && mouseX <= x + WIDTH - 15 && mouseY >= contentY && mouseY <= contentY + ROW_H;
                if (inRow) {
                    if (!"default".equals(profile) && inRect(mouseX, mouseY, x + WIDTH - 48, contentY + 4, 16, 14)) {
                        confirmProfile = profile;
                        return true;
                    }
                    if (Oxevy.configManager.loadProfile(profile)) {
                        setMessage("Loaded profile: " + profile);
                    } else {
                        setMessage("Failed to load profile");
                    }
                    refreshProfiles();
                    return true;
                }
                contentY += ROW_STEP;
            }

            // Bottom buttons
            int barY = y + HEIGHT - 34;
            if (inRect(mouseX, mouseY, x + 15, barY, 80, 20)) {
                Oxevy.configManager.save();
                refreshProfiles();
                setMessage("Config saved");
                return true;
            }
            if (inRect(mouseX, mouseY, x + 105, barY, 80, 20)) {
                Oxevy.configManager.load();
                refreshProfiles();
                setMessage("Config loaded from disk");
                return true;
            }
            if (inRect(mouseX, mouseY, x + 195, barY, 80, 20)) {
                showCreateField = true;
                createField.setValue("");
                createField.setFocused(true);
                return true;
            }
            if (inRect(mouseX, mouseY, x + WIDTH - 55, barY, 40, 20)) {
                refreshProfiles();
                setMessage("Refreshed");
                return true;
            }
        }

        if (button == 1) {
            onClose();
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void commitCreate() {
        String name = createField.getValue().trim().toLowerCase().replace(" ", "_");
        if (name.isEmpty() || !ConfigManager.isValidName(name)) {
            setMessage("Invalid profile name");
            createField.setValue("");
            createField.setFocused(true);
            return;
        }
        if (profiles.contains(name)) {
            setMessage("Profile already exists: " + name);
            createField.setValue("");
            createField.setFocused(true);
            return;
        }
        if (Oxevy.configManager.saveProfile(name)) {
            setMessage("Created profile: " + name);
        } else {
            setMessage("Failed to create profile");
        }
        closeCreate();
        refreshProfiles();
    }

    private void closeCreate() {
        showCreateField = false;
        createField.setValue("");
        createField.setFocused(false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (confirmProfile != null || showCreateField) return true;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 10)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (showCreateField) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeCreate();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commitCreate();
                return true;
            }
            return createField.keyPressed(input);
        }
        if (confirmProfile != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                confirmProfile = null;
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                if (Oxevy.configManager.deleteProfile(confirmProfile)) {
                    setMessage("Deleted profile: " + confirmProfile);
                } else {
                    setMessage("Failed to delete profile");
                }
                refreshProfiles();
                confirmProfile = null;
                return true;
            }
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (showCreateField) {
            return createField.charTyped(input);
        }
        return super.charTyped(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void setMessage(String msg) {
        this.message = msg;
        this.messageTimer = 100;
    }
}

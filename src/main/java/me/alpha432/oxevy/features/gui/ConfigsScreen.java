package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
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

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    private List<String> profiles;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String message = "";
    private int messageTimer = 0;
    private boolean showCreateField = false;
    private EditBox createField;

    // action confirm
    private String confirmProfile = null;
    private String confirmAction = null;

    public ConfigsScreen() {
        super(Component.literal("Config Manager"));
        refreshProfiles();
    }

    @Override
    protected void init() {
        createField = new EditBox(font, width / 2 - 80, height / 2 + 10, 160, 18, Component.literal("Profile name"));
        createField.setMaxLength(32);
        createField.setVisible(showCreateField);
        addWidget(createField);
    }

    private void refreshProfiles() {
        profiles = Oxevy.configManager.listConfigs();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int x = (context.guiWidth() - WIDTH) / 2;
        int y = (context.guiHeight() - HEIGHT) / 2;

        context.fill(0, 0, context.guiWidth(), context.guiHeight(), new Color(0, 0, 0, 150).getRGB());

        RenderUtil.rect(context, x, y, x + WIDTH, y + HEIGHT, 0xFF121212);
        RenderUtil.rect(context, x, y, x + WIDTH, y + 2, ClickGuiModule.getInstance().color.getValue().getRGB());

        context.drawString(font, "Config Manager", x + 15, y + 12, 0xFFAAAAAA);

        String cur = Oxevy.configManager.getCurrentConfig();
        context.drawString(font, "Current: " + cur, x + WIDTH - 15 - font.width("Current: " + cur), y + 12, 0xFF55FF55);

        // Profile list
        int contentY = y + 35 - scrollOffset;
        int idx = 0;
        String current = Oxevy.configManager.getCurrentConfig();
        for (String profile : profiles) {
            boolean hovered = mouseX >= x + 15 && mouseX <= x + WIDTH - 15 && mouseY >= contentY && mouseY <= contentY + 22;
            boolean isCurrent = profile.equals(current);

            int bgColor = isCurrent ? 0xFF2A2A3A : (hovered ? 0x1AFFFFFF : 0x12FFFFFF);
            RenderUtil.roundRect(context, x + 15, contentY, WIDTH - 30, 22, 6f, bgColor);

            if (isCurrent) {
                RenderUtil.rect(context, x + 15, contentY, x + 17, contentY + 22, ClickGuiModule.getInstance().color.getValue().getRGB());
            }

            context.drawString(font, profile, x + 24, contentY + 7, isCurrent ? 0xFFFFFFFF : 0xFFE6E6E6);

            // Delete button (not for default)
            if (!"default".equals(profile)) {
                boolean delHover = mouseX >= x + WIDTH - 50 && mouseX <= x + WIDTH - 34 && mouseY >= contentY + 4 && mouseY <= contentY + 18;
                RenderUtil.roundRect(context, x + WIDTH - 50, contentY + 4, 16, 14, 4f, delHover ? 0x44FF5555 : 0x22FFFFFF);
                context.drawString(font, "x", x + WIDTH - 44, contentY + 7, 0xFFFF5555);
            }

            idx++;
            contentY += 28;
        }

        maxScroll = Math.max(0, contentY - (y + HEIGHT) + 50);

        // Bottom action bar
        int barY = y + HEIGHT - 30;

        // Save button
        boolean saveHover = mouseX >= x + 15 && mouseX <= x + 85 && mouseY >= barY && mouseY <= barY + 20;
        RenderUtil.roundRect(context, x + 15, barY, 70, 20, 4f, saveHover ? 0x33FFFFFF : 0x22FFFFFF);
        context.drawString(font, "Save", x + 35, barY + 6, 0xFF55FF55);

        // Load button
        boolean loadHover = mouseX >= x + 95 && mouseX <= x + 165 && mouseY >= barY && mouseY <= barY + 20;
        RenderUtil.roundRect(context, x + 95, barY, 70, 20, 4f, loadHover ? 0x33FFFFFF : 0x22FFFFFF);
        context.drawString(font, "Load", x + 118, barY + 6, 0xFF55CCFF);

        // Create button
        boolean createHover = mouseX >= x + 175 && mouseX <= x + 245 && mouseY >= barY && mouseY <= barY + 20;
        RenderUtil.roundRect(context, x + 175, barY, 70, 20, 4f, createHover ? 0x33FFFFFF : 0x22FFFFFF);
        context.drawString(font, "Create", x + 193, barY + 6, 0xFFFFFF55);

        // Refresh button
        boolean refHover = mouseX >= x + WIDTH - 45 && mouseX <= x + WIDTH - 15 && mouseY >= barY && mouseY <= barY + 20;
        RenderUtil.roundRect(context, x + WIDTH - 45, barY, 30, 20, 4f, refHover ? 0x33FFFFFF : 0x22FFFFFF);
        context.drawString(font, "R", x + WIDTH - 32, barY + 6, 0xFFAAAAAA);

        // Create field overlay
        if (showCreateField) {
            int fx = width / 2 - 100;
            int fy = height / 2 - 40;
            int fw = 200;
            int fh = 90;
            RenderUtil.rect(context, fx - 10, fy - 10, fx + fw + 10, fy + fh + 10, 0xE0121212);
            RenderUtil.rect(context, fx - 10, fy - 10, fx + fw + 10, fy - 8, ClickGuiModule.getInstance().color.getValue().getRGB());
            context.drawString(font, "New Profile Name", fx, fy + 2, 0xFFAAAAAA);
            createField.setX(fx);
            createField.setY(fy + 20);
            createField.setWidth(fw);
            createField.visible = true;
            createField.render(context, mouseX, mouseY, delta);

            // Confirm button
            boolean conHover = mouseX >= fx && mouseX <= fx + 80 && mouseY >= fy + 48 && mouseY <= fy + 68;
            RenderUtil.roundRect(context, fx, fy + 48, 80, 20, 4f, conHover ? 0x33FFFFFF : 0x22FFFFFF);
            context.drawString(font, "Create", fx + 22, fy + 54, 0xFF55FF55);

            // Cancel button
            boolean canHover = mouseX >= fx + 90 && mouseX <= fx + 200 && mouseY >= fy + 48 && mouseY <= fy + 68;
            RenderUtil.roundRect(context, fx + 90, fy + 48, 110, 20, 4f, canHover ? 0x33FFFFFF : 0x22FFFFFF);
            context.drawString(font, "Cancel", fx + 128, fy + 54, 0xFFFF5555);
        } else {
            createField.visible = false;
        }

        // Confirmation dialog
        if (confirmProfile != null) {
            int fx = width / 2 - 100;
            int fy = height / 2 - 30;
            int fw = 200;
            int fh = 70;
            RenderUtil.rect(context, fx - 10, fy - 10, fx + fw + 10, fy + fh + 10, 0xE0121212);
            context.drawString(font, "Delete profile: " + confirmProfile + "?", fx, fy + 2, 0xFFFF5555);
            boolean yesHover = mouseX >= fx && mouseX <= fx + 80 && mouseY >= fy + 28 && mouseY <= fy + 48;
            RenderUtil.roundRect(context, fx, fy + 28, 80, 20, 4f, yesHover ? 0x33FFFFFF : 0x22FFFFFF);
            context.drawString(font, "Yes", fx + 30, fy + 34, 0xFFFF5555);
            boolean noHover = mouseX >= fx + 90 && mouseX <= fx + 200 && mouseY >= fy + 28 && mouseY <= fy + 48;
            RenderUtil.roundRect(context, fx + 90, fy + 28, 110, 20, 4f, noHover ? 0x33FFFFFF : 0x22FFFFFF);
            context.drawString(font, "Cancel", fx + 128, fy + 34, 0xFFAAAAAA);
        }

        if (messageTimer > 0) messageTimer--;
        if (!message.isEmpty() && messageTimer > 0) {
            context.drawString(font, message, x + 15, y + HEIGHT - 50, 0xFF55FF55);
        }

        super.render(context, mouseX, mouseY, delta);
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
                int fx = width / 2 - 100;
                int fy = height / 2 - 30;
                boolean yesHover = mouseX >= fx && mouseX <= fx + 80 && mouseY >= fy + 28 && mouseY <= fy + 48;
                boolean noHover = mouseX >= fx + 90 && mouseX <= fx + 200 && mouseY >= fy + 28 && mouseY <= fy + 48;
                if (yesHover) {
                    Oxevy.configManager.deleteProfile(confirmProfile);
                    refreshProfiles();
                    setMessage("Deleted profile: " + confirmProfile);
                    confirmProfile = null;
                    return true;
                }
                if (noHover) {
                    confirmProfile = null;
                    return true;
                }
                return true;
            }

            // Create field dialog
            if (showCreateField) {
                int fx = width / 2 - 100;
                int fy = height / 2 - 40;
                boolean conHover = mouseX >= fx && mouseX <= fx + 80 && mouseY >= fy + 48 && mouseY <= fy + 68;
                boolean canHover = mouseX >= fx + 90 && mouseX <= fx + 200 && mouseY >= fy + 48 && mouseY <= fy + 68;
                if (conHover) {
                    String name = createField.getValue().trim().toLowerCase().replace(" ", "_");
                    if (!name.isEmpty() && !profiles.contains(name)) {
                        Oxevy.configManager.saveProfile(name);
                        refreshProfiles();
                        setMessage("Created profile: " + name);
                    }
                    showCreateField = false;
                    createField.setValue("");
                    return true;
                }
                if (canHover) {
                    showCreateField = false;
                    createField.setValue("");
                    return true;
                }
                return true;
            }

            // Click on a profile row
            int contentY = y + 35 - scrollOffset;
            for (int idx = 0; idx < profiles.size(); idx++) {
                String profile = profiles.get(idx);
                boolean inRow = mouseX >= x + 15 && mouseX <= x + WIDTH - 15 && mouseY >= contentY && mouseY <= contentY + 22;
                if (inRow) {
                    // Delete button
                    if (!"default".equals(profile)) {
                        boolean delHover = mouseX >= x + WIDTH - 50 && mouseX <= x + WIDTH - 34 && mouseY >= contentY + 4 && mouseY <= contentY + 18;
                        if (delHover) {
                            confirmProfile = profile;
                            return true;
                        }
                    }
                    // Click on row = load
                    Oxevy.configManager.loadProfile(profile);
                    refreshProfiles();
                    setMessage("Loaded profile: " + profile);
                    return true;
                }
                contentY += 28;
            }

            // Bottom buttons
            int barY = y + HEIGHT - 30;

            boolean saveHover = mouseX >= x + 15 && mouseX <= x + 85 && mouseY >= barY && mouseY <= barY + 20;
            if (saveHover) {
                Oxevy.configManager.save();
                refreshProfiles();
                setMessage("Config saved");
                return true;
            }

            boolean loadHover = mouseX >= x + 95 && mouseX <= x + 165 && mouseY >= barY && mouseY <= barY + 20;
            if (loadHover) {
                Oxevy.configManager.load();
                refreshProfiles();
                setMessage("Config loaded from disk");
                return true;
            }

            boolean createHover = mouseX >= x + 175 && mouseX <= x + 245 && mouseY >= barY && mouseY <= barY + 20;
            if (createHover) {
                showCreateField = true;
                createField.setValue("");
                return true;
            }

            boolean refHover = mouseX >= x + WIDTH - 45 && mouseX <= x + WIDTH - 15 && mouseY >= barY && mouseY <= barY + 20;
            if (refHover) {
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 10)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (showCreateField) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                showCreateField = false;
                createField.setValue("");
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                String name = createField.getValue().trim().toLowerCase().replace(" ", "_");
                if (!name.isEmpty() && !profiles.contains(name)) {
                    Oxevy.configManager.saveProfile(name);
                    refreshProfiles();
                    setMessage("Created profile: " + name);
                }
                showCreateField = false;
                createField.setValue("");
                return true;
            }
            return createField.keyPressed(input);
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

package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.util.HWID;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class BanScreen extends Screen {

    private static final int DIALOG_W = 440;
    private static final int DIALOG_H = 270;
    private static final int BTN_W = 175;
    private static final int BTN_H = 24;
    private static final int BTN_GAP = 12;

    private float exitHover = 0f;
    private float reloadHover = 0f;
    private boolean checking = false;
    private String status = null;

    public BanScreen() {
        super(Component.literal("Oxevy - Banned"));
    }

    private int btnRowY(int dialogY) {
        return dialogY + DIALOG_H - BTN_H - 30;
    }

    private int exitBtnX(int dialogX) {
        int rowStart = dialogX + (DIALOG_W - (BTN_W * 2 + BTN_GAP)) / 2;
        return rowStart + BTN_W + BTN_GAP;
    }

    private int reloadBtnX(int dialogX) {
        return dialogX + (DIALOG_W - (BTN_W * 2 + BTN_GAP)) / 2;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, new Color(0, 0, 0, 175).getRGB());

        int x = (width - DIALOG_W) / 2;
        int y = (height - DIALOG_H) / 2;
        int accent = ClickGuiModule.getInstance().color.getValue().getRGB();

        RenderUtil.rect(context, x, y, x + DIALOG_W, y + DIALOG_H, 0xFF121212);
        RenderUtil.rect(context, x, y, x + DIALOG_W, y + 3, accent);

        String title = "[!] YOU HAVE BEEN BANNED BY ADMINISTRATOR [!]";
        context.drawString(font, title, x + (DIALOG_W - font.width(title)) / 2, y + 20, 0xFFFF4444, true);

        RenderUtil.rect(context, x + 20, y + 36, x + DIALOG_W - 20, y + 37, 0x1EFFFFFF);

        String line1 = "Your Hardware ID (HWID) has been flagged.";
        String line2 = "Access from this device has been revoked.";
        context.drawString(font, line1, x + (DIALOG_W - font.width(line1)) / 2, y + 56, 0xFFCCCCCC, false);
        context.drawString(font, line2, x + (DIALOG_W - font.width(line2)) / 2, y + 72, 0xFFCCCCCC, false);

        RenderUtil.rect(context, x + 20, y + 88, x + DIALOG_W - 20, y + 89, 0x1EFFFFFF);

        String what = "What is an HWID?";
        context.drawString(font, what, x + (DIALOG_W - font.width(what)) / 2, y + 104, 0xFFFFFFFF, false);

        String[] hwidInfo = {
            "An HWID (Hardware ID) is a unique fingerprint of",
            "your computer, built from its components such as",
            "MAC addresses, CPU, motherboard and disk serials.",
            "Even if you change nick or account, this device",
            "stays banned until it is removed from the database."
        };
        int infoY = y + 122;
        for (String line : hwidInfo) {
            context.drawString(font, line, x + (DIALOG_W - font.width(line)) / 2, infoY, 0xFF94A3B8, false);
            infoY += 14;
        }

        int btnY = btnRowY(y);

        int reloadX = reloadBtnX(x);
        boolean reloadHovered = mouseX >= reloadX && mouseX <= reloadX + BTN_W && mouseY >= btnY && mouseY <= btnY + BTN_H;
        float reloadTarget = reloadHovered ? 1f : 0f;
        reloadHover += (reloadTarget - reloadHover) * 12f * delta;
        reloadHover = Math.max(0f, Math.min(1f, reloadHover));
        boolean reloadDisabled = checking;

        RenderUtil.roundRect(context, reloadX, btnY, BTN_W, BTN_H, 4f, reloadDisabled ? 0x14FFFFFF : (reloadHovered ? 0x33FFFFFF : 0x22FFFFFF));
        RenderUtil.rect(context, reloadX, btnY, reloadX + BTN_W, btnY + BTN_H, 0x55FFFFFF, 1f);
        int reloadIndicatorW = 2 + (int) (reloadHover * 3);
        RenderUtil.roundRect(context, reloadX, btnY + 2, reloadIndicatorW, BTN_H - 4, 1f, 0xAAFFFFFF);
        String reloadText = checking ? "CHECKING..." : "RELOAD";
        int reloadTextColor = reloadDisabled ? 0xFF777777 : (reloadHovered ? 0xFFFFFFFF : 0xFFCCCCCC);
        context.drawString(font, reloadText, reloadX + (BTN_W - font.width(reloadText)) / 2, btnY + (BTN_H - 8) / 2, reloadTextColor, false);

        int exitX = exitBtnX(x);
        boolean exitHovered = mouseX >= exitX && mouseX <= exitX + BTN_W && mouseY >= btnY && mouseY <= btnY + BTN_H;
        float exitTarget = exitHovered ? 1f : 0f;
        exitHover += (exitTarget - exitHover) * 12f * delta;
        exitHover = Math.max(0f, Math.min(1f, exitHover));

        RenderUtil.roundRect(context, exitX, btnY, BTN_W, BTN_H, 4f, exitHovered ? 0x33FFFFFF : 0x22FFFFFF);
        RenderUtil.rect(context, exitX, btnY, exitX + BTN_W, btnY + BTN_H, 0x55FF4444, 1f);
        int exitIndicatorW = 2 + (int) (exitHover * 3);
        RenderUtil.roundRect(context, exitX, btnY + 2, exitIndicatorW, BTN_H - 4, 1f, 0xAAFF4444);
        String exitText = "EXIT GAME";
        int exitTextColor = exitHovered ? 0xFFFFFFFF : 0xFFCCCCCC;
        context.drawString(font, exitText, exitX + (BTN_W - font.width(exitText)) / 2, btnY + (BTN_H - 8) / 2, exitTextColor, false);

        if (status != null && !status.isEmpty()) {
            int statusColor = status.startsWith("Banned") || status.startsWith("Still") ? 0xFFFF6666 : 0xFF66FF88;
            context.drawString(font, status, x + (DIALOG_W - font.width(status)) / 2, btnY - 14, statusColor, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            int x = (width - DIALOG_W) / 2;
            int y = (height - DIALOG_H) / 2;
            int btnY = btnRowY(y);
            int reloadX = reloadBtnX(x);
            int exitX = exitBtnX(x);

            if (!checking && click.x() >= reloadX && click.x() <= reloadX + BTN_W && click.y() >= btnY && click.y() <= btnY + BTN_H) {
                reloadBan();
                return true;
            }
            if (click.x() >= exitX && click.x() <= exitX + BTN_W && click.y() >= btnY && click.y() <= btnY + BTN_H) {
                minecraft.stop();
                return true;
            }
        }
        return true;
    }

    private void reloadBan() {
        checking = true;
        status = "Checking database...";
        String nick = null;
        try {
            String profileNick = Minecraft.getInstance().getGameProfile().name();
            if (profileNick != null && !profileNick.isEmpty()) nick = profileNick;
        } catch (Exception ignored) {
        }
        String hwid = HWID.get();
        Oxevy.modDataManager.checkBan(nick, hwid, bannedResult -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                checking = false;
                if (bannedResult) {
                    status = "Still banned on this device.";
                } else {
                    Oxevy.unban();
                    if (mc.level == null) {
                        mc.setScreen(new OxevyMainMenu());
                    } else {
                        mc.setScreen(null);
                    }
                }
            });
        });
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_1 || input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            minecraft.stop();
            return true;
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

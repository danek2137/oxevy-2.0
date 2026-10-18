package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.manager.ProxyManager;
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

public class ProxiesScreen extends net.minecraft.client.gui.screens.Screen {
    private static final Minecraft mc = Minecraft.getInstance();
    private final net.minecraft.client.gui.screens.Screen parent;

    private static final int WIDTH = 480;
    private static final int HEIGHT = 340;
    private static final int ROW_STEP = 28;
    private static final int ROW_H = 22;

    private float openAnimation = 0f;

    private List<ProxyManager.ProxyInfo> proxiesList;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String message = "";
    private int messageTimer = 0;
    private boolean showCreateField = false;
    private EditBox createField;

    public ProxiesScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(Component.literal("Proxy Manager"));
        this.parent = parent;
        refreshProxies();
        Oxevy.proxyManager.pingAll();
    }

    @Override
    protected void init() {
        createField = new EditBox(CustomFont.font(), width / 2 - 100, height / 2 + 10, 200, 18, Component.literal("Format: name:ip:port or ip:port"));
        createField.setMaxLength(64);
        createField.setVisible(false);
        createField.setValue("");
    }

    private void refreshProxies() {
        proxiesList = Oxevy.proxyManager.getProxies();
        int last = proxiesList.size() * ROW_STEP;
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

        context.drawString(CustomFont.font(), "Proxy Manager", x + 15, y + 12, 0xFFAAAAAA);

        ProxyManager.ProxyInfo active = Oxevy.proxyManager.getActiveProxy();
        String activeText = active == null ? "Direct Connection" : "Proxy: " + active.name;
        context.drawString(CustomFont.font(), activeText, x + WIDTH - 15 - CustomFont.font().width(activeText), y + 12, active == null ? 0xFF94A3B8 : 0xFF55FF55);

        // Proxy list
        int contentY = y + 35 - scrollOffset;
        for (ProxyManager.ProxyInfo proxy : proxiesList) {
            if (contentY >= y + 35 && contentY <= y + HEIGHT - 60) {
                boolean hovered = mouseX >= x + 15 && mouseX <= x + WIDTH - 15 && mouseY >= contentY && mouseY <= contentY + ROW_H;
                boolean isActive = (proxy == active);

                int bgColor = isActive ? 0xFF2A2A3A : (hovered ? 0x1AFFFFFF : 0x12FFFFFF);
                RenderUtil.roundRect(context, x + 15, contentY, WIDTH - 30, ROW_H, 6f, bgColor);

                if (isActive) {
                    RenderUtil.roundRect(context, x + 15, contentY, 4, ROW_H, 2f, accent());
                }

                String label = proxy.name + " (" + proxy.ip + ":" + proxy.port + ")";
                context.drawString(CustomFont.font(), label, x + 27, contentY + 7, isActive ? 0xFFFFFFFF : 0xFFE6E6E6);

                // Ping indicator
                String pingText;
                int pingColor;
                if (proxy.pinging) {
                    pingText = "...";
                    pingColor = 0xFFAAAAAA;
                } else if (proxy.ping == -1) {
                    pingText = "? ms";
                    pingColor = 0xFF888888;
                } else if (proxy.ping == -2) {
                    pingText = "Offline";
                    pingColor = 0xFFFF5555;
                } else {
                    pingText = proxy.ping + " ms";
                    if (proxy.ping < 150) pingColor = 0xFF55FF55;
                    else if (proxy.ping < 300) pingColor = 0xFFFFFF55;
                    else pingColor = 0xFFFF8855;
                }
                context.drawString(CustomFont.font(), pingText, x + WIDTH - 75 - CustomFont.font().width(pingText), contentY + 7, pingColor);

                // Delete button for custom proxies
                if (proxy.custom) {
                    boolean delHover = mouseX >= x + WIDTH - 48 && mouseX <= x + WIDTH - 32 && mouseY >= contentY + 4 && mouseY <= contentY + 18;
                    RenderUtil.roundRect(context, x + WIDTH - 48, contentY + 4, 16, 14, 4f, delHover ? 0x55FF5555 : 0x22FFFFFF);
                    context.drawString(CustomFont.font(), "x", x + WIDTH - 43, contentY + 7, 0xFFFF5555);
                }
            }
            contentY += ROW_STEP;
        }

        // Bottom action bar
        int barY = y + HEIGHT - 34;
        drawButton(context, x + 15, barY, 100, 20, "Add Proxy", 0xFF55FF55, mouseX, mouseY);
        drawButton(context, x + 125, barY, 100, 20, "Refresh Pings", 0xFF55CCFF, mouseX, mouseY);
        drawButton(context, x + 235, barY, 100, 20, "Disable Proxy", 0xFFFF8855, mouseX, mouseY);
        drawButton(context, x + WIDTH - 115, barY, 100, 20, "Back", 0xFFFF5555, mouseX, mouseY);

        // Add Proxy field overlay
        if (showCreateField) {
            int fx = x + WIDTH / 2 - 120;
            int fy = y + HEIGHT / 2 - 60;
            int fw = 240;
            int fh = 120;
            RenderUtil.rect(context, fx, fy, fx + fw, fy + fh, 0xE0121212);
            RenderUtil.rect(context, fx, fy, fx + fw, fy + 2, accent());
            context.drawString(CustomFont.font(), "Add Proxy (ip:port)", fx + 15, fy + 12, 0xFFAAAAAA);

            createField.setX(fx + 15);
            createField.setY(fy + 30);
            createField.setWidth(fw - 30);
            createField.setVisible(true);
            createField.render(context, mouseX, mouseY, delta);

            drawButton(context, fx + 15, fy + 64, 100, 20, "Add", 0xFF55FF55, mouseX, mouseY);
            drawButton(context, fx + 125, fy + 64, 100, 20, "Cancel", 0xFFFF5555, mouseX, mouseY);
        } else {
            createField.visible = false;
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
            // Create field dialog
            if (showCreateField) {
                int fx = x + WIDTH / 2 - 120;
                int fy = y + HEIGHT / 2 - 60;
                if (inRect(mouseX, mouseY, fx + 15, fy + 64, 100, 20)) {
                    commitAdd();
                    return true;
                }
                if (inRect(mouseX, mouseY, fx + 125, fy + 64, 100, 20)) {
                    closeAdd();
                    return true;
                }
                if (inRect(mouseX, mouseY, fx + 15, fy + 30, 210, 18)) {
                    createField.mouseClicked(click, doubled);
                    createField.setFocused(true);
                    return true;
                }
                return true;
            }

            // Click on a proxy row
            int contentY = y + 35 - scrollOffset;
            for (ProxyManager.ProxyInfo proxy : proxiesList) {
                if (contentY >= y + 35 && contentY + ROW_H <= y + HEIGHT - 60) {
                    boolean inRow = mouseX >= x + 15 && mouseX <= x + WIDTH - 15 && mouseY >= contentY && mouseY <= contentY + ROW_H;
                    if (inRow) {
                        if (proxy.custom && inRect(mouseX, mouseY, x + WIDTH - 48, contentY + 4, 16, 14)) {
                            Oxevy.proxyManager.removeProxy(proxy);
                            setMessage("Deleted proxy: " + proxy.name);
                            refreshProxies();
                            return true;
                        }
                        Oxevy.proxyManager.useProxy(proxy);
                        setMessage("Enabled proxy: " + proxy.name);
                        return true;
                    }
                }
                contentY += ROW_STEP;
            }

            // Bottom buttons
            int barY = y + HEIGHT - 34;
            if (inRect(mouseX, mouseY, x + 15, barY, 100, 20)) {
                showCreateField = true;
                createField.setValue("");
                createField.setFocused(true);
                return true;
            }
            if (inRect(mouseX, mouseY, x + 125, barY, 100, 20)) {
                Oxevy.proxyManager.pingAll();
                setMessage("Pinging proxies...");
                return true;
            }
            if (inRect(mouseX, mouseY, x + 235, barY, 100, 20)) {
                Oxevy.proxyManager.useProxy(null);
                setMessage("Disabled proxy (direct connection)");
                return true;
            }
            if (inRect(mouseX, mouseY, x + WIDTH - 115, barY, 100, 20)) {
                mc.setScreen(parent);
                return true;
            }
        }

        if (button == 1) {
            mc.setScreen(parent);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void commitAdd() {
        String val = createField.getValue().trim();
        if (val.isEmpty()) {
            setMessage("Invalid proxy format");
            return;
        }

        String name = "Proxy " + (proxiesList.size() + 1);
        String ip;
        int port;

        try {
            if (val.contains(":")) {
                String[] parts = val.split(":");
                if (parts.length == 3) {
                    name = parts[0];
                    ip = parts[1];
                    port = Integer.parseInt(parts[2]);
                } else if (parts.length == 2) {
                    ip = parts[0];
                    port = Integer.parseInt(parts[1]);
                } else {
                    throw new IllegalArgumentException();
                }
            } else {
                throw new IllegalArgumentException();
            }

            Oxevy.proxyManager.addProxy(name, ip, port);
            setMessage("Added proxy: " + name);
            closeAdd();
            refreshProxies();
            // Ping the newly added proxy
            Oxevy.proxyManager.pingProxy(proxiesList.get(proxiesList.size() - 1));
        } catch (Exception e) {
            setMessage("Error: Use format name:ip:port or ip:port");
            createField.setValue("");
            createField.setFocused(true);
        }
    }

    private void closeAdd() {
        showCreateField = false;
        createField.setValue("");
        createField.setFocused(false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (showCreateField) return true;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 10)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (showCreateField) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeAdd();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commitAdd();
                return true;
            }
            return createField.keyPressed(input);
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            mc.setScreen(parent);
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

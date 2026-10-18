package me.alpha432.oxevy.features.modules.misc;

import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public class AutoReconnect extends Module {
    private final Setting<Double> delay = num("Delay", 3.0, 0.5, 30.0);
    private final Setting<Integer> maxAttempts = num("MaxAttempts", 10, 1, 100);

    private ServerData lastServer;
    private boolean wasConnected;
    private long reconnectTime;
    private int attempts;
    private boolean pendingReconnect;

    public AutoReconnect() {
        super("AutoReconnect", "Automatically reconnects when disconnected from a server", Category.MISC);
    }

    @Override
    public void onEnable() {
        wasConnected = mc.level != null;
        if (wasConnected && mc.getCurrentServer() != null) {
            lastServer = mc.getCurrentServer();
        }
        pendingReconnect = false;
        attempts = 0;
    }

    @Override
    public void onTick() {
        boolean connected = mc.level != null && mc.getConnection() != null;

        if (connected) {
            if (!wasConnected) {
                lastServer = mc.getCurrentServer();
                attempts = 0;
                pendingReconnect = false;
            }
            wasConnected = true;
            return;
        }

        // Was connected, now disconnected start reconnect timer
        if (wasConnected && lastServer != null && !pendingReconnect) {
            if (attempts < maxAttempts.getValue()) {
                pendingReconnect = true;
                reconnectTime = System.currentTimeMillis() + (long) (delay.getValue() * 1000);
                attempts++;
                Command.sendMessage(String.format("AutoReconnect: reconnecting in %.1fs (attempt %d/%d)",
                    delay.getValue(), attempts, maxAttempts.getValue()));
            }
            wasConnected = false;
        }

        if (pendingReconnect && System.currentTimeMillis() >= reconnectTime) {
            doReconnect();
        }
    }

    private void doReconnect() {
        if (lastServer == null) {
            pendingReconnect = false;
            return;
        }

        Command.sendMessage("AutoReconnect: connecting to " + lastServer.ip);
        pendingReconnect = false;

        ConnectScreen.startConnecting(
            mc.screen,
            mc,
            ServerAddress.parseString(lastServer.ip),
            lastServer,
            false,
            null
        );
    }

    @Override
    public String getDisplayInfo() {
        if (pendingReconnect) {
            long left = Math.max(0, reconnectTime - System.currentTimeMillis());
            return String.format("%.1fs (%d/%d)", left / 1000.0, attempts, maxAttempts.getValue());
        }
        if (lastServer != null) return lastServer.ip;
        return null;
    }
}

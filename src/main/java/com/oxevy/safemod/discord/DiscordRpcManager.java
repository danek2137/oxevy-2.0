package com.oxevy.safemod.discord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DiscordRpcManager {
    private static final Logger LOG = LogManager.getLogger();
    private static final String DEFAULT_CLIENT_ID = "1503936816305143819";
    private static boolean enabled = false;
    private static long startTimestampSec = 0L;

    private DiscordRpcManager() {
    }

    public static void init(String clientId) {
        if (enabled) return;

        if (clientId == null || clientId.isEmpty()) {
            clientId = DEFAULT_CLIENT_ID;
        }

        if (!NativeDiscordRpc.isLoaded()) {
            LOG.warn("Discord RPC native library not loaded");
            return;
        }

        try {
            enabled = NativeDiscordRpc.initialize(clientId);
            if (enabled) {
                startTimestampSec = System.currentTimeMillis() / 1000L;
                LOG.info("Discord RPC ready (native bridge)");
            } else {
                LOG.debug("Discord RPC not available (Discord may not be running)");
            }
        } catch (Throwable t) {
            LOG.debug("Discord RPC init failed", t);
            enabled = false;
        }
    }

    public static void setStartTimestamp(long timestampSec) {
        startTimestampSec = timestampSec;
    }

    public static void updatePresence(String details, String state) {
        updatePresence(details, state, null, null);
    }

    public static void updatePresence(String details, String state, String largeImageKey) {
        updatePresence(details, state, largeImageKey, null);
    }

    public static void updatePresence(String details, String state, String largeImageKey, String smallImageKey) {
        if (!enabled) return;
        try {
            NativeDiscordRpc.updatePresence(details, state, largeImageKey, smallImageKey,
                    startTimestampSec, 0L);
        } catch (Throwable t) {
            LOG.debug("Discord RPC update failed", t);
        }
    }

    public static void shutdown() {
        if (!enabled) return;
        try {
            NativeDiscordRpc.shutdown();
        } catch (Throwable t) {
            LOG.debug("Discord RPC shutdown failed", t);
        }
        enabled = false;
        startTimestampSec = 0L;
    }

    public static boolean isEnabled() {
        return enabled;
    }
}

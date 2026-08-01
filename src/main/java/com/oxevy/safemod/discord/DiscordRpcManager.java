package com.oxevy.safemod.discord;

import dev.firstdark.rpc.DiscordRpc;
import dev.firstdark.rpc.enums.ActivityType;
import dev.firstdark.rpc.enums.ErrorCode;
import dev.firstdark.rpc.handlers.RPCEventHandler;
import dev.firstdark.rpc.models.DiscordRichPresence;
import dev.firstdark.rpc.models.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slf4j.helpers.NOPLogger;

public class DiscordRpcManager {
    private static final Logger LOG = LogManager.getLogger();
    private static final String DEFAULT_CLIENT_ID = "1531698504605630554";
    private static DiscordRpc rpc;
    private static boolean enabled = false;

    public static void init(String clientId) {
        if (enabled && rpc != null) return;

        if (clientId == null || clientId.isEmpty()) {
            clientId = DEFAULT_CLIENT_ID;
        }

        rpc = new DiscordRpc();
        rpc.setDebugMode(false);
        rpc.setLogger(NOPLogger.NOP_LOGGER);

        RPCEventHandler handler = new RPCEventHandler() {
            @Override
            public void ready(User user) {
                LOG.info("Discord RPC ready");
            }

            @Override
            public void disconnected(ErrorCode errorCode, String message) {
            }

            @Override
            public void errored(ErrorCode errorCode, String message) {
            }
        };

        try {
            rpc.init(clientId, handler, false);
            enabled = true;
        } catch (Exception e) {
            LOG.debug("Discord RPC not available (Discord may not be running)");
            rpc = null;
        }
    }

    public static void updatePresence(String details, String state) {
        if (!enabled || rpc == null) return;

        DiscordRichPresence presence = DiscordRichPresence.builder()
                .details(details)
                .state(state)
                .activityType(ActivityType.PLAYING)
                .build();

        rpc.updatePresence(presence);
    }

    public static void updatePresence(String details, String state, String largeImageKey) {
        if (!enabled || rpc == null) return;

        var builder = DiscordRichPresence.builder()
                .details(details)
                .state(state)
                .activityType(ActivityType.PLAYING);

        if (largeImageKey != null && !largeImageKey.isEmpty()) {
            builder.largeImageKey(largeImageKey);
        }

        rpc.updatePresence(builder.build());
    }

    public static void shutdown() {
        if (rpc != null) {
            try {
                rpc.shutdown();
            } catch (Exception e) {
            }
            rpc = null;
        }
        enabled = false;
    }

    public static boolean isEnabled() {
        return enabled;
    }
}

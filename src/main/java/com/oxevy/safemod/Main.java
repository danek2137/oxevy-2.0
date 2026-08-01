package com.oxevy.safemod;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Main implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        String clientId = System.getenv("OXEVY_DISCORD_CLIENT_ID");
        com.oxevy.safemod.discord.DiscordRpcManager.init(clientId);
    }
}

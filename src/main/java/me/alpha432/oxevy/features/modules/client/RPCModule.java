package me.alpha432.oxevy.features.modules.client;

import com.oxevy.safemod.discord.DiscordRpcManager;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.BuildConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RPCModule extends Module {
    private static final Logger LOG = LoggerFactory.getLogger("RPC");

    public final Setting<String> largeImage = str("Large Image", "oxevy");
    private boolean rpcInitialized = false;
    private int tickCounter = 0;
    private String lastDetails = "";
    private String lastState = "";
    private String lastImage = "";

    public RPCModule() {
        super("RPC", "Discord Rich Presence", Category.CLIENT);
    }

    @Override
    public void onEnable() {
        if (rpcInitialized) return;
        String clientId = System.getenv("OXEVY_DISCORD_CLIENT_ID");
        DiscordRpcManager.init(clientId);
        DiscordRpcManager.setStartTimestamp(System.currentTimeMillis() / 1000L);
        rpcInitialized = true;
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        DiscordRpcManager.shutdown();
        rpcInitialized = false;
        lastDetails = "";
        lastState = "";
        lastImage = "";
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        if (!rpcInitialized) {
            onEnable();
            if (!rpcInitialized) return;
        }

        if (++tickCounter < 100) return;
        tickCounter = 0;

        String server = "Singleplayer";
        if (mc.getCurrentServer() != null) {
            server = mc.getCurrentServer().ip;
        }

        String details = "Playing Minecraft " + BuildConfig.VERSION;
        String state = server + " | " + mc.player.getName().getString();
        String image = largeImage.getValue();

        if (details.equals(lastDetails) && state.equals(lastState) && image.equals(lastImage)) {
            return;
        }

        lastDetails = details;
        lastState = state;
        lastImage = image;
        DiscordRpcManager.updatePresence(details, state, image, null);
    }
}

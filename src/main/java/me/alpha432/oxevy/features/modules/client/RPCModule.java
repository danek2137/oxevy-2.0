package me.alpha432.oxevy.features.modules.client;

import com.oxevy.safemod.discord.DiscordRpcManager;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.BuildConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RPCModule extends Module {
    private static final Logger LOG = LoggerFactory.getLogger("RPC");

    public final Setting<String> largeImage = str("Large Image", "oxevy");

    public RPCModule() {
        super("RPC", "Discord Rich Presence", Category.CLIENT);
    }

    @Override
    public void onEnable() {
        String clientId = System.getenv("OXEVY_DISCORD_CLIENT_ID");
        DiscordRpcManager.init(clientId);
        DiscordRpcManager.setStartTimestamp(System.currentTimeMillis() / 1000L);
    }

    @Override
    public void onDisable() {
        DiscordRpcManager.shutdown();
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || nullCheck()) return;

        String server = "Singleplayer";
        if (mc.getCurrentServer() != null) {
            server = mc.getCurrentServer().ip;
        }

        String details = "Playing Minecraft " + BuildConfig.VERSION;
        String state = server + " | " + mc.player.getName().getString();
        DiscordRpcManager.updatePresence(details, state, largeImage.getValue(), null);
    }
}

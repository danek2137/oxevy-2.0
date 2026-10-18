package me.alpha432.oxevy;

import me.alpha432.oxevy.manager.*;
import me.alpha432.oxevy.model.CosmeticModelLoader;
import me.alpha432.oxevy.network.LocalAssetServer;
import me.alpha432.oxevy.network.OxevyNetworkManager;
import me.alpha432.oxevy.sound.OxevySounds;
import me.alpha432.oxevy.util.BuildConfig;
import me.alpha432.oxevy.util.HWID;
import me.alpha432.oxevy.util.TextUtil;
import me.alpha432.oxevy.features.Feature;
import me.alpha432.oxevy.features.gui.BanScreen;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.web.ClickGuiWebBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Oxevy implements ModInitializer, ClientModInitializer {
    public static float TIMER = 1f;

    public static boolean tempDisabled = false;
    public static boolean destroyed = false;
    public static volatile boolean banned = false;
    public static java.util.Set<String> tempDisabledModules = new java.util.HashSet<>();
    public static final java.util.Set<String> bannedEnabledModules = new java.util.HashSet<>();

    public static boolean hitboxesEnabled = false;
    public static float hitboxesExpand = 1.5f;
    public static boolean hitboxesEntities = true;
    public static boolean hitboxesPlayers = true;
    public static boolean hitboxesMobs = true;

    public static final Logger LOGGER = LogManager.getLogger("Oxevy");
    public static ServerManager serverManager;
    public static ColorManager colorManager;
    public static RotationManager rotationManager;
    public static PositionManager positionManager;
    public static HoleManager holeManager;
    public static EventManager eventManager;
    public static SpeedManager speedManager;
    public static CommandManager commandManager;
    public static FriendManager friendManager;
    public static ModuleManager moduleManager;
    public static ConfigManager configManager;
    public static TargetManager targetManager;
    public static CapeManager capeManager;
    public static OxevyUsersManager oxevyUsersManager;
    public static ModDataManager modDataManager;

    @Override
    public void onInitialize() {
        LOGGER.info("Pre-initializing {} v{}",
                BuildConfig.NAME, BuildConfig.VERSION);
        configManager = new ConfigManager();
        eventManager = new EventManager();
        serverManager = new ServerManager();
        targetManager = new TargetManager();
        rotationManager = new RotationManager();
        positionManager = new PositionManager();
        friendManager = new FriendManager();
        oxevyUsersManager = new OxevyUsersManager();
        modDataManager = new ModDataManager();
        colorManager = new ColorManager();
        commandManager = new CommandManager();
        moduleManager = new ModuleManager();
        speedManager = new SpeedManager();
        holeManager = new HoleManager();
        capeManager = new CapeManager();

        TextUtil.init();
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing {}", BuildConfig.NAME);

        showWelcomeScreen();

        long startTime = System.nanoTime();

        // Register cosmetic model loader
        CosmeticModelLoader.register();

        OxevySounds.init();
        eventManager.init();
        commandManager.init();
        moduleManager.init();
        friendManager.init();
        oxevyUsersManager.init();
        OxevyNetworkManager.init();
        LocalAssetServer.start();

        configManager.addConfig(capeManager);
        configManager.load();
        colorManager.init();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ClickGuiWebBridge.stop();
            SelfCosmeticsManager.onGameShutdown();
            CosmeticModelLoader.getInstance().close();
            configManager.save();
        }));

        checkHwidBan();

        long endTime = System.nanoTime();

        LOGGER.info("Initialized {} in {}ms",
                BuildConfig.NAME, (endTime - startTime) / 1000000.0);
    }

    private static void showWelcomeScreen() {
        if (banned) return;
        try {
            System.setProperty("java.awt.headless", "false");
        } catch (Exception ignored) {
        }
        try {
            com.mojang.authlib.GameProfile profile = Minecraft.getInstance().getGameProfile();
            if (profile == null) {
                LOGGER.warn("WelcomeScreen: no game profile, overlay skipped");
                return;
            }
            String nick = profile.getName();
            if (nick != null && !nick.isEmpty()) {
                me.alpha432.oxevy.util.WelcomeScreen.show(nick, profile.getId().toString());
            } else {
                LOGGER.warn("WelcomeScreen: empty player name, overlay skipped");
            }
        } catch (Exception e) {
            LOGGER.warn("WelcomeScreen: failed to show overlay", e);
        }
    }

    private static void checkHwidBan() {
        String hwid = HWID.get();
        LOGGER.info("Device HWID: {}", hwid);
        String nick = null;
        try {
            String profileNick = Minecraft.getInstance().getGameProfile().getName();
            if (profileNick != null && !profileNick.isEmpty()) nick = profileNick;
        } catch (Exception ignored) {
        }
        final String finalNick = nick;
        modDataManager.checkBan(finalNick, hwid, bannedResult -> {
            LOGGER.info("HWID ban check result: nick={} banned={}", finalNick, bannedResult);
            if (!bannedResult) return;
            modDataManager.reportHwid(finalNick, hwid);
            applyBan();
        });
    }

    public static void applyBan() {
        if (banned) return;
        banned = true;
        LOGGER.warn("This copy of Oxevy is banned on this device (HWID). All modules locked.");
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (moduleManager != null) {
                    bannedEnabledModules.clear();
                    moduleManager.getModules().stream()
                            .filter(Feature::isEnabled)
                            .forEach(m -> bannedEnabledModules.add(m.getName()));
                    moduleManager.getModules().stream()
                            .filter(Feature::isEnabled)
                            .forEach(m -> m.disable());
                }
                if (mc.screen == null || !(mc.screen instanceof BanScreen)) {
                    mc.setScreen(new BanScreen());
                }
            });
        } catch (Exception ignored) {
        }
    }

    public static void unban() {
        if (!banned) return;
        banned = false;
        LOGGER.info("HWID ban lifted. Restoring previous module state.");
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (moduleManager != null) {
                    for (String name : bannedEnabledModules) {
                        Module m = moduleManager.getModuleByName(name);
                        if (m != null && !m.isEnabled()) m.enable();
                    }
                }
                bannedEnabledModules.clear();
            });
        } catch (Exception ignored) {
        }
    }
}




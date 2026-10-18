package me.alpha432.oxevy.features.modules.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.manager.CommandManager;
import me.alpha432.oxevy.util.BuildConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Macro system with exploit protection.
 * Allows binding commands and GUI actions to keys.
 * Blocks sign/book exploit by validating input sources.
 */
public class MacroModule extends Module {
    private static final Logger LOG = LogManager.getLogger("Macros");
    private static final String MACROS_FILE = "oxevy/macros.json";

    // Macro types
    public enum MacroType {
        COMMAND,    // Execute a command (e.g., ".togg KillAura")
        GUI,        // Open a GUI screen (e.g., "clickgui", "mainmenu")
        MESSAGE     // Send a chat message
    }

    public static class Macro {
        public String name;
        public MacroType type;
        public String value;      // Command, GUI name, or message
        public int keybind;       // GLFW key code
        public boolean enabled;

        public Macro(String name, MacroType type, String value, int keybind) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.keybind = keybind;
            this.enabled = true;
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", name);
            obj.addProperty("type", type.name());
            obj.addProperty("value", value);
            obj.addProperty("keybind", keybind);
            obj.addProperty("enabled", enabled);
            return obj;
        }

        public static Macro fromJson(JsonObject obj) {
            String name = obj.get("name").getAsString();
            MacroType type = MacroType.valueOf(obj.get("type").getAsString());
            String value = obj.get("value").getAsString();
            int keybind = obj.get("keybind").getAsInt();
            boolean enabled = obj.get("enabled").getAsBoolean();
            Macro macro = new Macro(name, type, value, keybind);
            macro.enabled = enabled;
            return macro;
        }
    }

    // Settings
    public final Setting<Boolean> blockSignExploit = bool("Block Sign Exploit", true);

    // Storage
    private final Map<String, Macro> macros = new ConcurrentHashMap<>();
    private final File macrosFile;

    public MacroModule() {
        super("Macros", "Keybind macros for commands and GUI actions", Category.CLIENT);
        macrosFile = new File(Minecraft.getInstance().gameDirectory, MACROS_FILE);
        loadMacros();
    }

    @Override
    public void onEnable() {
        // Register key handlers
    }

    @Override
    public void onDisable() {
        saveMacros();
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        // Check keybinds for macros
        for (Macro macro : macros.values()) {
            if (!macro.enabled || macro.keybind <= 0) continue;

            // Only process if key was just pressed (not held)
            if (isKeyJustPressed(macro.keybind)) {
                executeMacro(macro);
            }
        }
    }

    /**
     * Check if a key was just pressed this tick (not held).
     * Uses a simple state tracking to detect key press events.
     */
    private final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();

    private boolean isKeyJustPressed(int keyCode) {
        long window = Minecraft.getInstance().getWindow().handle();
        boolean isDown = GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;

        if (isDown && !pressedKeys.contains(keyCode)) {
            pressedKeys.add(keyCode);
            return true; // Just pressed
        } else if (!isDown) {
            pressedKeys.remove(keyCode);
        }
        return false;
    }

    /**
     * Execute a macro with exploit protection.
     * Validates that the execution comes from a legitimate key press,
     * not from a sign/book click exploit.
     */
    private void executeMacro(Macro macro) {
        Minecraft mc = Minecraft.getInstance();

        // Exploit protection: Check if we're in a sign/book editing screen
        // The sign exploit works by putting keybinds in sign text that get
        // "clicked" when the player interacts with the sign.
        // We block this by checking the current screen.
        if (blockSignExploit.getValue()) {
            Screen currentScreen = mc.screen;
            if (currentScreen != null) {
                String screenName = currentScreen.getClass().getSimpleName();
                // Block execution if in sign editor, book editor, or chat screen
                // (these are common vectors for the exploit)
                if (screenName.contains("Sign") || screenName.contains("Book")
                        || screenName.contains("ChatScreen") || screenName.contains("CommandBlock")) {
                    LOG.warn("[Macros] Blocked macro execution from potential exploit screen: {}", screenName);
                    Command.sendMessage("{red}[Macros] Exploit blocked: Macro execution prevented from {}", screenName);
                    return;
                }
            }
        }

        try {
            switch (macro.type) {
                case COMMAND -> {
                    // Execute command through command manager
                    String cmd = macro.value;
                    if (!cmd.startsWith(".") && !cmd.startsWith("/")) {
                        cmd = "." + cmd;
                    }
                    Oxevy.commandManager.onChatSent(cmd);
                    Command.sendMessage("{green}[Macros] Executed command: {}", cmd);
                }
                case GUI -> {
                    String gui = macro.value.toLowerCase();
                    switch (gui) {
                        case "clickgui" -> me.alpha432.oxevy.features.gui.WebClickGuiScreen.open();
                        case "mainmenu" -> me.alpha432.oxevy.features.gui.WebMainMenuScreen.open();
                        case "settings" -> mc.setScreen(new me.alpha432.oxevy.features.gui.OxevySettingsScreen());
                        default -> Command.sendMessage("{red}[Macros] Unknown GUI: {}", gui);
                    }
                }
                case MESSAGE -> {
                    if (mc.player != null && mc.player.connection != null) {
                        String msg = macro.value;
                        // Send as command (chat messages need "/" prefix in modern MC)
                        if (!msg.startsWith("/") && !msg.startsWith(".")) {
                            msg = "/" + msg;
                        }
                        mc.player.connection.sendCommand(msg.startsWith(".") ? msg.substring(1) : msg.substring(1));
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("[Macros] Failed to execute macro '{}': {}", macro.name, e.getMessage());
            Command.sendMessage("{red}[Macros] Error executing macro: {}", e.getMessage());
        }
    }

    /**
     * Add a new macro.
     */
    public boolean addMacro(String name, MacroType type, String value, int keybind) {
        if (macros.containsKey(name.toLowerCase())) {
            return false;
        }
        Macro macro = new Macro(name, type, value, keybind);
        macros.put(name.toLowerCase(), macro);
        saveMacros();
        return true;
    }

    /**
     * Remove a macro by name.
     */
    public boolean removeMacro(String name) {
        Macro removed = macros.remove(name.toLowerCase());
        if (removed != null) {
            saveMacros();
            return true;
        }
        return false;
    }

    /**
     * Get a macro by name.
     */
    public Macro getMacro(String name) {
        return macros.get(name.toLowerCase());
    }

    /**
     * List all macros.
     */
    public Collection<Macro> getAllMacros() {
        return Collections.unmodifiableCollection(macros.values());
    }

    /**
     * Toggle macro enabled state.
     */
    public boolean toggleMacro(String name) {
        Macro macro = macros.get(name.toLowerCase());
        if (macro != null) {
            macro.enabled = !macro.enabled;
            saveMacros();
            return true;
        }
        return false;
    }

    /**
     * Set macro keybind.
     */
    public boolean setMacroKeybind(String name, int keybind) {
        Macro macro = macros.get(name.toLowerCase());
        if (macro != null) {
            macro.keybind = keybind;
            saveMacros();
            return true;
        }
        return false;
    }

    /**
     * Load macros from file.
     */
    private void loadMacros() {
        if (!macrosFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(macrosFile)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                for (JsonElement elem : array) {
                    if (elem.isJsonObject()) {
                        Macro macro = Macro.fromJson(elem.getAsJsonObject());
                        macros.put(macro.name.toLowerCase(), macro);
                    }
                }
            }
            LOG.info("[Macros] Loaded {} macros", macros.size());
        } catch (Exception e) {
            LOG.error("[Macros] Failed to load macros: {}", e.getMessage());
        }
    }

    /**
     * Save macros to file.
     */
    private void saveMacros() {
        try {
            macrosFile.getParentFile().mkdirs();
            JsonArray array = new JsonArray();
            for (Macro macro : macros.values()) {
                array.add(macro.toJson());
            }
            try (FileWriter writer = new FileWriter(macrosFile)) {
                writer.write(array.toString());
            }
        } catch (Exception e) {
            LOG.error("[Macros] Failed to save macros: {}", e.getMessage());
        }
    }

    /**
     * Convert key name to GLFW key code.
     */
    public static int keyNameToCode(String keyName) {
        if (keyName == null || keyName.isEmpty()) return -1;
        try {
            return InputConstants.getKey(keyName.toUpperCase()).getValue();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Convert GLFW key code to display name.
     */
    public static String keyCodeToName(int keyCode) {
        if (keyCode <= 0) return "None";
        return InputConstants.getKey(new net.minecraft.client.input.KeyEvent(keyCode, 0, 0)).getName();
    }
}
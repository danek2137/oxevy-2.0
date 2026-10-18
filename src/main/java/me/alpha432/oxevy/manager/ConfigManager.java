package me.alpha432.oxevy.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.alpha432.oxevy.features.Feature;
import me.alpha432.oxevy.features.settings.Bind;
import me.alpha432.oxevy.features.settings.EnumConverter;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.traits.Jsonable;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConfigManager");
    private static final Path BASE_PATH = FabricLoader.getInstance().getGameDir().resolve("oxevy");
    private static final Path CONFIGS_PATH = BASE_PATH.resolve("configs");
    private static final Path CURRENT_FILE = BASE_PATH.resolve("current.txt");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Jsonable> jsonables = new LinkedList<>();
    private String currentConfig = "default";

    public ConfigManager() {
        loadCurrentConfigName();
    }

    /**
     * First-launch flags.
     *
     * <p>Deliberately a property of the installation rather than of a profile:
     * switching config profiles should not replay an introduction. Stored
     * beside {@code current.txt} and written immediately, so a crash can never
     * resurrect the welcome tour.
     */
    private static final Path STATE_FILE = BASE_PATH.resolve("state.json");

    public static boolean hasSeenWelcome() {
        try {
            if (!Files.exists(STATE_FILE)) return false;
            com.google.gson.JsonElement root = JsonParser.parseString(Files.readString(STATE_FILE));
            if (root == null || !root.isJsonObject()) return false;
            com.google.gson.JsonElement seen = root.getAsJsonObject().get("welcomeShown");
            return seen != null && seen.isJsonPrimitive() && seen.getAsBoolean();
        } catch (Throwable t) {
            LOGGER.warn("Could not read {}", STATE_FILE, t);
            return false;
        }
    }

    public static void markWelcomeSeen() {
        try {
            Files.createDirectories(BASE_PATH);
            // Merge instead of overwrite: other client-wide flags may live here later.
            com.google.gson.JsonObject state = new com.google.gson.JsonObject();
            if (Files.exists(STATE_FILE)) {
                try {
                    com.google.gson.JsonElement existing = JsonParser.parseString(Files.readString(STATE_FILE));
                    if (existing != null && existing.isJsonObject()) state = existing.getAsJsonObject();
                } catch (Throwable ignored) {
                }
            }
            state.addProperty("welcomeShown", true);
            Files.writeString(STATE_FILE, GSON.toJson(state));
        } catch (Throwable t) {
            LOGGER.error("Could not write {}", STATE_FILE, t);
        }
    }

    public String getCurrentConfig() {
        return currentConfig;
    }

    public List<String> listConfigs() {
        if (!CONFIGS_PATH.toFile().exists()) return List.of("default");
        try (Stream<Path> dirs = Files.list(CONFIGS_PATH)) {
            List<String> names = dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(ConfigManager::isValidName)
                    .sorted()
                    .collect(Collectors.toList());
            if (!names.contains("default")) names.add(0, "default");
            return names;
        } catch (IOException e) {
            return List.of("default");
        }
    }

    public boolean saveProfile(String name) {
        name = name.trim().toLowerCase();
        if (!isValidName(name)) return false;
        mkdirs();
        Path configDir = CONFIGS_PATH.resolve(name);
        try {
            Files.createDirectories(configDir);
            for (Jsonable jsonable : jsonables) {
                String fn = jsonable.getFileName();
                if (fn == null || fn.isEmpty()) continue;
                Files.writeString(configDir.resolve(fn), GSON.toJson(jsonable.toJson()));
            }
            currentConfig = name;
            saveCurrentConfigName();
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save profile '{}'", name, e);
            return false;
        }
    }

    public boolean loadProfile(String name) {
        name = name.trim().toLowerCase();
        if (!isValidName(name)) return false;
        Path configDir = CONFIGS_PATH.resolve(name);
        if (!configDir.toFile().exists()) {
            if ("default".equals(name)) {
                currentConfig = "default";
                saveCurrentConfigName();
                return true;
            }
            return false;
        }
        for (Jsonable jsonable : jsonables) {
            String fn = jsonable.getFileName();
            if (fn == null || fn.isEmpty()) continue;
            Path file = configDir.resolve(fn);
            if (!file.toFile().exists()) continue;
            try {
                jsonable.fromJson(JsonParser.parseString(Files.readString(file)));
            } catch (IOException e) {
                LOGGER.error("Failed to load '{}' from profile '{}'", fn, name, e);
            }
        }
        currentConfig = name;
        saveCurrentConfigName();
        return true;
    }

    public boolean deleteProfile(String name) {
        name = name.trim().toLowerCase();
        if ("default".equals(name) || !isValidName(name)) return false;
        Path configDir = CONFIGS_PATH.resolve(name);
        if (!configDir.toFile().exists()) return false;
        try {
            try (Stream<Path> files = Files.walk(configDir)) {
                files.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
            if (currentConfig.equals(name)) {
                currentConfig = "default";
                saveCurrentConfigName();
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to delete profile '{}'", name, e);
            return false;
        }
    }

    /**
     * Packs a profile into a single portable bundle:
     * {@code {"format":1,"name":"pvp","files":{"modules.json":{...},...}}}.
     * Returns null when the profile does not exist on disk.
     */
    public JsonObject exportProfile(String name) {
        name = name.trim().toLowerCase();
        if (!isValidName(name)) return null;
        Path configDir = CONFIGS_PATH.resolve(name);
        if (!configDir.toFile().exists()) return null;
        JsonObject files = new JsonObject();
        try (Stream<Path> stream = Files.list(configDir)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                String fn = p.getFileName().toString();
                if (!fn.endsWith(".json")) continue;
                try {
                    files.add(fn, JsonParser.parseString(Files.readString(p)));
                } catch (Exception e) {
                    LOGGER.warn("Skipping unreadable file '{}' in profile '{}'", fn, name);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to export profile '{}'", name, e);
            return null;
        }
        JsonObject bundle = new JsonObject();
        bundle.addProperty("format", 1);
        bundle.addProperty("name", name);
        bundle.add("files", files);
        return bundle;
    }

    /**
     * Writes a bundle produced by {@link #exportProfile} back to disk under a
     * (possibly different) profile name. Only plain {@code *.json} file entries
     * are accepted; anything malformed is ignored, and the profile is only
     * created if at least one valid file landed.
     */
    public boolean importProfile(String name, JsonObject bundle) {
        name = name.trim().toLowerCase();
        if (!isValidName(name) || bundle == null) return false;
        JsonElement filesEl = bundle.get("files");
        if (filesEl == null || !filesEl.isJsonObject()) return false;
        JsonObject files = filesEl.getAsJsonObject();
        if (files.size() == 0) return false;
        mkdirs();
        Path configDir = CONFIGS_PATH.resolve(name);
        int written = 0;
        try {
            Files.createDirectories(configDir);
            for (var entry : files.entrySet()) {
                String fn = entry.getKey();
                if (fn == null || !fn.endsWith(".json")) continue;
                if (fn.contains("..") || fn.contains("/") || fn.contains("\\")) continue;
                JsonElement value = entry.getValue();
                if (value == null || (!value.isJsonObject() && !value.isJsonArray())) continue;
                Files.writeString(configDir.resolve(fn), GSON.toJson(value));
                written++;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to import profile '{}'", name, e);
            return false;
        }
        return written > 0;
    }

    /** Zaps every config file from disk (used by destruct). */
    public void wipeAll() {
        try {
            if (CONFIGS_PATH.toFile().exists()) {
                try (Stream<Path> files = Files.walk(CONFIGS_PATH)) {
                    files.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                            });
                }
            }
            if (CURRENT_FILE.toFile().exists()) Files.deleteIfExists(CURRENT_FILE);
        } catch (IOException e) {
            LOGGER.error("Failed to wipe configs", e);
        }
    }

    public void addConfig(Jsonable jsonable) {
        jsonables.add(jsonable);
    }

    public void load() {
        mkdirs();
        Path configDir = CONFIGS_PATH.resolve(currentConfig);
        if (!configDir.toFile().exists()) {
            migrateOldFiles();
            configDir = CONFIGS_PATH.resolve(currentConfig);
        }
        for (Jsonable jsonable : jsonables) {
            try {
                String read = Files.readString(configDir.resolve(jsonable.getFileName()));
                jsonable.fromJson(JsonParser.parseString(read));
            } catch (Throwable ignored) {}
        }
    }

    public void save() {
        mkdirs();
        Path configDir = CONFIGS_PATH.resolve(currentConfig);
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
            return;
        }
        for (Jsonable jsonable : jsonables) {
            try {
                Files.writeString(configDir.resolve(jsonable.getFileName()), GSON.toJson(jsonable.toJson()));
            } catch (Throwable e) {
                LOGGER.error("Failed to write to file", e);
            }
        }
    }

    public Path getConfigFolder() {
        return BASE_PATH;
    }

    private void mkdirs() {
        if (!BASE_PATH.toFile().exists()) {
            boolean success = BASE_PATH.toFile().mkdirs();
            if (!success) throw new RuntimeException("Failed to create needed directories!");
        }
    }

    private void loadCurrentConfigName() {
        try {
            if (CURRENT_FILE.toFile().exists()) {
                currentConfig = Files.readString(CURRENT_FILE).trim().toLowerCase();
                if (!isValidName(currentConfig)) {
                    currentConfig = "default";
                }
            }
        } catch (IOException e) {
            currentConfig = "default";
        }
    }

    /** Profiles may only be made of lowercase letters, digits, underscores or dashes. */
    public static boolean isValidName(String name) {
        return name != null && name.matches("[a-z0-9_-]+");
    }

    private void saveCurrentConfigName() {
        try {
            mkdirs();
            Files.writeString(CURRENT_FILE, currentConfig);
        } catch (IOException e) {
            LOGGER.error("Failed to save current config name", e);
        }
    }

    private void migrateOldFiles() {
        if (CONFIGS_PATH.toFile().exists()) return;
        Path modulesFile = BASE_PATH.resolve("modules.json");
        if (!modulesFile.toFile().exists()) return;
        Path defaultDir = CONFIGS_PATH.resolve("default");
        try {
            Files.createDirectories(defaultDir);
            try (Stream<Path> files = Files.list(BASE_PATH)) {
                files.filter(f -> f.toString().endsWith(".json"))
                        .filter(f -> !"current.txt".equals(f.getFileName().toString()))
                        .forEach(f -> {
                            try {
                                Path dest = defaultDir.resolve(f.getFileName());
                                if (!dest.toFile().exists()) Files.move(f, dest);
                            } catch (IOException ignored) {}
                        });
            }
            currentConfig = "default";
            saveCurrentConfigName();
        } catch (IOException e) {
            LOGGER.error("Failed to migrate old config files", e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void setValueFromJson(Feature feature, Setting setting, JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        switch (setting.getType()) {
            case "Boolean" -> setting.setValue(element.getAsBoolean());
            case "Double" -> setting.setValue(element.getAsDouble());
            case "Float" -> setting.setValue(element.getAsFloat());
            case "Integer" -> setting.setValue(element.getAsInt());
            case "String" -> setting.setValue(element.getAsString().replace("_", " "));
            case "Bind" -> setting.setValue(new Bind(element.getAsInt()));
            case "Color" -> {
                try {
                    String colorStr = element.getAsString();
                    String[] parts = colorStr.split(",");
                    if (parts.length == 4) {
                        int r = Integer.parseInt(parts[0]);
                        int g = Integer.parseInt(parts[1]);
                        int b = Integer.parseInt(parts[2]);
                        int a = Integer.parseInt(parts[3]);
                        setting.setValue(new Color(r, g, b, a));
                    }
                } catch (Exception exception) {
                    LOGGER.error("Error parsing color for: {} : {}", feature.getName(), setting.getName());
                }
            }
            case "Pos" -> {
                try {
                    String posStr = element.getAsString();
                    String[] parts = posStr.split(",");
                    if (parts.length == 2) {
                        float x = Float.parseFloat(parts[0]);
                        float y = Float.parseFloat(parts[1]);
                        setting.setValue(new Vector2f(x, y));
                    }
                } catch (Exception exception) {
                    LOGGER.error("Error parsing position for: {} : {}", feature.getName(), setting.getName());
                }
            }
            case "Position" -> {
                try {
                    String posStr = element.getAsString();
                    String[] parts = posStr.split(",");
                    if (parts.length == 2) {
                        float x = Float.parseFloat(parts[0]);
                        float y = Float.parseFloat(parts[1]);
                        setting.setValue(new Vector2f(x, y));
                    }
                } catch (Exception exception) {
                    LOGGER.error("Error parsing position for: {} : {}", feature.getName(), setting.getName());
                }
            }
            case "Enum" -> {
                try {
                    EnumConverter converter = new EnumConverter(setting.getValue().getClass());
                    Enum value = converter.doBackward(element);
                    setting.setValue(value);
                } catch (Exception exception) {
                    LOGGER.error("Error parsing enum for {}.{}: {}", feature.getName(), setting.getName(), exception);
                }
            }
            case "Runnable" -> {
                // Runnable settings are not serializable, ignore
            }
            default -> LOGGER.error("Unknown Setting type for: {} : {}", feature.getName(), setting.getName());
        }
    }
}

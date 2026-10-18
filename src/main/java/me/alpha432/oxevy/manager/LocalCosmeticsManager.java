package me.alpha432.oxevy.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalCosmeticsManager {

    private static final Path CACHE_FILE = FabricLoader.getInstance().getGameDir()
            .resolve("oxevy").resolve("local_cosmetics.json");
    private static final Path THEMES_FILE = FabricLoader.getInstance().getGameDir()
            .resolve("oxevy").resolve("cosmetic_themes.json");
    private static final Gson GSON = new Gson();

    private static final Set<String> equipped = ConcurrentHashMap.newKeySet();
    private static volatile boolean loaded = false;

    private LocalCosmeticsManager() {}

    public static Set<String> getEquipped() {
        if (!loaded) load();
        return equipped;
    }

    public static void equip(String id) {
        if (!loaded) load();
        equipped.add(id);
        persist();
    }

    public static void unequip(String id) {
        if (!loaded) load();
        equipped.remove(id);
        persist();
    }

    public static boolean isEquipped(String id) {
        if (!loaded) load();
        return equipped.contains(id);
    }

    public static void load() {
        if (loaded) return;
        try {
            if (!Files.exists(CACHE_FILE)) {
                loaded = true;
                return;
            }
            String content = Files.readString(CACHE_FILE);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            JsonArray arr = obj.getAsJsonArray("equipped");
            if (arr != null) {
                for (JsonElement e : arr) {
                    if (e.isJsonPrimitive()) {
                        equipped.add(e.getAsString());
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        loaded = true;
    }

    private static synchronized void persist() {
        try {
            Path parent = CACHE_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject obj = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String id : equipped) {
                arr.add(id);
            }
            obj.add("equipped", arr);
            Files.writeString(CACHE_FILE, obj.toString());
        } catch (Exception ignored) {
        }
    }

    // Theme management
    public static void saveTheme(String name, Set<String> items) {
        try {
            Path parent = THEMES_FILE.getParent();
            if (parent != null) Files.createDirectories(parent);
            JsonObject allThemes;
            if (Files.exists(THEMES_FILE)) {
                String content = Files.readString(THEMES_FILE);
                allThemes = JsonParser.parseString(content).getAsJsonObject();
            } else {
                allThemes = new JsonObject();
            }
            JsonArray arr = new JsonArray();
            for (String id : items) arr.add(id);
            allThemes.add(name, arr);
            Files.writeString(THEMES_FILE, allThemes.toString());
        } catch (Exception ignored) {}
    }

    public static List<String> loadTheme(String name) {
        try {
            if (!Files.exists(THEMES_FILE)) return List.of();
            String content = Files.readString(THEMES_FILE);
            JsonObject allThemes = JsonParser.parseString(content).getAsJsonObject();
            JsonElement theme = allThemes.get(name);
            if (theme == null || !theme.isJsonArray()) return List.of();
            List<String> items = new ArrayList<>();
            for (JsonElement e : theme.getAsJsonArray()) {
                if (e.isJsonPrimitive()) items.add(e.getAsString());
            }
            return items;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static Set<String> listThemes() {
        try {
            if (!Files.exists(THEMES_FILE)) return Set.of();
            String content = Files.readString(THEMES_FILE);
            JsonObject allThemes = JsonParser.parseString(content).getAsJsonObject();
            return allThemes.keySet();
        } catch (Exception e) {
            return Set.of();
        }
    }

    public static void deleteTheme(String name) {
        try {
            if (!Files.exists(THEMES_FILE)) return;
            String content = Files.readString(THEMES_FILE);
            JsonObject allThemes = JsonParser.parseString(content).getAsJsonObject();
            allThemes.remove(name);
            Files.writeString(THEMES_FILE, allThemes.toString());
        } catch (Exception ignored) {}
    }
}
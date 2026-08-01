package me.alpha432.oxevy.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.util.traits.Jsonable;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CapeManager implements Jsonable {

    public enum CapeMode {
        NONE,
        UNLOCKED,
        CUSTOM
    }

    public static class CapeEntry {
        private String name;
        private String url;
        private String localFile;

        public CapeEntry() {}

        public CapeEntry(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getLocalFile() { return localFile; }
        public void setLocalFile(String localFile) { this.localFile = localFile; }
    }

    private static final Path OXEVEY_PATH = FabricLoader.getInstance().getGameDir().resolve("oxevy");
    private static final Path CAPES_DIR = OXEVEY_PATH.resolve("capes");
    private static final Path UNLOCKED_CAPES_FILE = OXEVEY_PATH.resolve("unlocked_capes.json");
    private static final Path LAUNCHER_SELECTION_FILE = OXEVEY_PATH.resolve("launcher_cape.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String REPO_API = "https://api.github.com/repos/danek123321/oxevy-assets/contents/";
    private static final String REPO_RAW = "https://raw.githubusercontent.com/danek123321/oxevy-assets/main/";
    private static final Set<String> IGNORED_FILES = Set.of("README.md", "watermark.png", ".gitignore");

    private CapeMode mode = CapeMode.NONE;
    private final List<CapeEntry> unlockedCapes = new ArrayList<>();
    private final List<CapeEntry> uploadedCapes = new ArrayList<>();
    private final List<CapeEntry> repoCapes = new ArrayList<>();
    private int activeUnlockedIndex = 0;
    private String customCapeUrl = "";
    private boolean repoFetched = false;

    public CapeManager() {
        loadUnlockedCapesFromFile();
        loadLauncherSelection();
        mkdirs();
        fetchRepoCapesAsync();
    }

    public CapeMode getMode() { return mode; }
    public void setMode(CapeMode mode) { this.mode = mode; }

    public List<CapeEntry> getUnlockedCapes() { return unlockedCapes; }
    public List<CapeEntry> getRepoCapes() { return repoCapes; }
    public boolean isRepoFetched() { return repoFetched; }

    public int getActiveUnlockedIndex() { return activeUnlockedIndex; }
    public void setActiveUnlockedIndex(int index) {
        if (index >= 0 && index < unlockedCapes.size()) {
            activeUnlockedIndex = index;
        }
    }

    public String getCustomCapeUrl() { return customCapeUrl; }
    public void setCustomCapeUrl(String url) { this.customCapeUrl = url; }

    public void reloadFromLauncher() {
        loadUnlockedCapesFromFile();
        loadLauncherSelection();
    }

    public String getActiveCapeUrl() {
        switch (mode) {
            case UNLOCKED:
                if (activeUnlockedIndex >= 0 && activeUnlockedIndex < unlockedCapes.size()) {
                    CapeEntry entry = unlockedCapes.get(activeUnlockedIndex);
                    if (entry.getLocalFile() != null) {
                        return "http://127.0.0.1:8765/capes/" + entry.getLocalFile();
                    }
                    if (entry.getUrl() != null && !entry.getUrl().isEmpty()) {
                        return entry.getUrl();
                    }
                }
                return null;
            case CUSTOM:
                if (customCapeUrl != null && !customCapeUrl.isEmpty()) {
                    return customCapeUrl;
                }
                return null;
            default:
                return null;
        }
    }

    public Path saveUploadedCape(byte[] data, String fileName) {
        mkdirs();
        String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path filePath = CAPES_DIR.resolve(safeName);
        try {
            Files.write(filePath, data);
            CapeEntry entry = new CapeEntry("Custom: " + safeName, null);
            entry.setLocalFile(safeName);
            uploadedCapes.add(entry);
            rebuildUnlockedList();
            activeUnlockedIndex = unlockedCapes.size() - 1;
            mode = CapeMode.UNLOCKED;
            saveSelectionToLauncherFile();
            return filePath;
        } catch (IOException e) {
            Oxevy.LOGGER.error("Failed to save uploaded cape", e);
            return null;
        }
    }

    public void selectRepoCape(int index) {
        if (index >= 0 && index < repoCapes.size()) {
            // Find or add this cape in unlockedCapes
            CapeEntry selected = repoCapes.get(index);
            int found = -1;
            for (int i = 0; i < unlockedCapes.size(); i++) {
                if (selected.getUrl().equals(unlockedCapes.get(i).getUrl())) {
                    found = i;
                    break;
                }
            }
            if (found == -1) {
                unlockedCapes.add(selected);
                found = unlockedCapes.size() - 1;
            }
            activeUnlockedIndex = found;
            mode = CapeMode.UNLOCKED;
            saveSelectionToLauncherFile();
        }
    }

    public void saveSelectionToLauncherFile() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("mode", mode.name());
            if (mode == CapeMode.UNLOCKED && activeUnlockedIndex >= 0 && activeUnlockedIndex < unlockedCapes.size()) {
                CapeEntry entry = unlockedCapes.get(activeUnlockedIndex);
                obj.addProperty("url", entry.getUrl());
                obj.addProperty("name", entry.getName());
                obj.addProperty("index", activeUnlockedIndex);
            } else if (mode == CapeMode.CUSTOM) {
                obj.addProperty("url", customCapeUrl);
            }
            Files.writeString(LAUNCHER_SELECTION_FILE, GSON.toJson(obj));
            Oxevy.LOGGER.info("[CapeManager] Saved cape selection to launcher_cape.json");
        } catch (IOException e) {
            Oxevy.LOGGER.error("[CapeManager] Failed to save launcher cape selection", e);
        }
    }

    /**
     * Fetches cape list from the GitHub repo in a background thread.
     */
    public void fetchRepoCapesAsync() {
        new Thread(() -> {
            try {
                fetchRepoCapes();
            } catch (Exception e) {
                Oxevy.LOGGER.error("[CapeManager] Failed to fetch repo capes", e);
            }
        }, "CapeRepoFetch").start();
    }

    /**
     * Fetches the list of cape PNGs from the GitHub repo, ignoring README.md and watermark.png.
     */
    private void fetchRepoCapes() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(REPO_API).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int status = conn.getResponseCode();
        if (status != 200) {
            Oxevy.LOGGER.warn("[CapeManager] GitHub API returned {}", status);
            return;
        }

        JsonArray files;
        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            files = JsonParser.parseReader(reader).getAsJsonArray();
        }

        synchronized (repoCapes) {
            repoCapes.clear();
            for (JsonElement elem : files) {
                JsonObject file = elem.getAsJsonObject();
                String name = file.get("name").getAsString();
                if (IGNORED_FILES.contains(name)) continue;
                if (!name.toLowerCase().endsWith(".png")) continue;

                String title = name.substring(0, name.lastIndexOf('.'));
                String url = REPO_RAW + name;
                CapeEntry entry = new CapeEntry(title, url);
                repoCapes.add(entry);
            }
        }

        repoFetched = true;
        Oxevy.LOGGER.info("[CapeManager] Fetched {} capes from repo", repoCapes.size());
    }

    public void loadLauncherSelection() {
        try {
            if (!Files.exists(LAUNCHER_SELECTION_FILE)) return;
            String content = Files.readString(LAUNCHER_SELECTION_FILE);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();

            if (obj.has("mode")) {
                try {
                    mode = CapeMode.valueOf(obj.get("mode").getAsString().toUpperCase());
                } catch (Exception ignored) {}
            }

            if (obj.has("index")) {
                int idx = obj.get("index").getAsInt();
                if (idx >= 0 && idx < unlockedCapes.size()) {
                    activeUnlockedIndex = idx;
                    mode = CapeMode.UNLOCKED;
                }
            }

            if (obj.has("url")) {
                String url = obj.get("url").getAsString();
                if (!url.isEmpty()) {
                    customCapeUrl = url;
                    boolean found = false;
                    for (int i = 0; i < unlockedCapes.size(); i++) {
                        if (url.equals(unlockedCapes.get(i).getUrl())) {
                            activeUnlockedIndex = i;
                            mode = CapeMode.UNLOCKED;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        mode = CapeMode.CUSTOM;
                    }
                }
            }

            if (obj.has("file")) {
                String fileName = obj.get("file").getAsString();
                CapeEntry entry = new CapeEntry("Launcher Cape", null);
                entry.setLocalFile(fileName);
                uploadedCapes.add(entry);
                rebuildUnlockedList();
                activeUnlockedIndex = unlockedCapes.size() - 1;
                mode = CapeMode.UNLOCKED;
            }

            Oxevy.LOGGER.info("[CapeManager] Loaded launcher cape selection: mode={}, index={}", mode, activeUnlockedIndex);
        } catch (Exception e) {
            Oxevy.LOGGER.error("[CapeManager] Failed to read launcher cape selection", e);
        }
    }

    private void rebuildUnlockedList() {
        List<CapeEntry> combined = new ArrayList<>();
        for (CapeEntry e : unlockedCapes) {
            if (e.getLocalFile() == null) {
                combined.add(e);
            }
        }
        combined.addAll(uploadedCapes);
        unlockedCapes.clear();
        unlockedCapes.addAll(combined);
    }

    private void loadUnlockedCapesFromFile() {
        try {
            unlockedCapes.removeIf(e -> e.getLocalFile() == null);

            if (Files.exists(UNLOCKED_CAPES_FILE)) {
                String content = Files.readString(UNLOCKED_CAPES_FILE);
                JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                if (obj.has("capes")) {
                    Type listType = new TypeToken<List<CapeEntry>>(){}.getType();
                    List<CapeEntry> loaded = GSON.fromJson(obj.get("capes"), listType);
                    for (CapeEntry entry : loaded) {
                        if (entry.getName() != null && entry.getUrl() != null) {
                            unlockedCapes.add(entry);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Oxevy.LOGGER.error("Failed to load unlocked capes", e);
        }
    }

    private void mkdirs() {
        try {
            Files.createDirectories(CAPES_DIR);
        } catch (IOException e) {
            Oxevy.LOGGER.error("Failed to create capes directory", e);
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("mode", mode.name());
        obj.addProperty("activeUnlockedIndex", activeUnlockedIndex);
        obj.addProperty("customCapeUrl", customCapeUrl);
        return obj;
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) return;
        try {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("mode")) {
                mode = CapeMode.valueOf(obj.get("mode").getAsString());
            }
            if (obj.has("activeUnlockedIndex")) {
                activeUnlockedIndex = obj.get("activeUnlockedIndex").getAsInt();
            }
            if (obj.has("customCapeUrl")) {
                customCapeUrl = obj.get("customCapeUrl").getAsString();
            }
        } catch (Exception e) {
            Oxevy.LOGGER.error("Failed to load cape config", e);
        }
    }

    @Override
    public String getFileName() {
        return "capes.json";
    }
}

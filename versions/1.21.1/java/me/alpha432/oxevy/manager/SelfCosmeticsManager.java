package me.alpha432.oxevy.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.CosmeticsModule;
import me.alpha432.oxevy.mixin.accessor.PlayerInfoAccessor;
import me.alpha432.oxevy.mixin.accessor.SkinManagerAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.resources.PlayerSkin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SelfCosmeticsManager {

    private static final long SCAN_INTERVAL_MS = 2000L;
    private static final long LIST_REFRESH_MS = 300_000L;

    private static final Map<String, String> capeUrls = new ConcurrentHashMap<>();
    private static final Map<String, String> skinUrls = new ConcurrentHashMap<>();
    private static final Set<String> noCape = ConcurrentHashMap.newKeySet();
    private static final Set<String> noSkin = ConcurrentHashMap.newKeySet();
    private static final Set<String> capeFetching = ConcurrentHashMap.newKeySet();
    private static final Set<String> skinFetching = ConcurrentHashMap.newKeySet();
    private static final Set<String> renderActive = ConcurrentHashMap.newKeySet();
    private static final Map<String, MinecraftProfileTextures> baseTextures = new ConcurrentHashMap<>();

    // ── oxevy user list (nicknames of players with the client installed) ──
    // Downloaded once per world via /read; cosmetic lookups only happen for
    // players that appear in this list instead of every nearby player.
    private static final Set<String> oxevyNicknames = ConcurrentHashMap.newKeySet();
    private static volatile boolean listLoaded = false;

    private static volatile boolean fetchStarted = false;
    private static final Object LOCK = new Object();
    private static volatile CountDownLatch fetchDone = new CountDownLatch(1);

    private static volatile ScheduledExecutorService scanner;
    private static final Path CACHE_FILE = FabricLoader.getInstance().getGameDir()
            .resolve("oxevy").resolve("player_cosmetics.json");
    private static final Gson GSON = new Gson();

    private SelfCosmeticsManager() {}

    public static void onWorldJoin() {
        renderActive.clear();
        loadCache();
        fetchStarted = false;
        fetchDone = new CountDownLatch(1);
        listLoaded = false;
        oxevyNicknames.clear();
        loadOxevyUsers();
        prefetch();
        startScanner();
    }

    public static void onGameShutdown() {
        stopScanner();
        renderActive.clear();
        capeUrls.clear();
        skinUrls.clear();
        noCape.clear();
        noSkin.clear();
        capeFetching.clear();
        skinFetching.clear();
        baseTextures.clear();
        try {
            Files.deleteIfExists(CACHE_FILE);
        } catch (Exception ignored) {
        }
    }

    public static boolean isCosmeticsEnabled() {
        ModuleManager mm = Oxevy.moduleManager;
        if (mm == null) return true;
        CosmeticsModule module = mm.getModuleByClass(CosmeticsModule.class);
        return module == null || module.isEnabled();
    }

    public static void onModuleToggled() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.execute(SelfCosmeticsManager::revertAllApplied);
    }

    private static void revertAllApplied() {
        if (renderActive.isEmpty()) return;
        for (String key : Set.copyOf(renderActive)) {
            try {
                revertCosmeticsToPlayer(UUID.fromString(key));
            } catch (Exception ignored) {
            }
        }
        renderActive.clear();
    }

    public static String getCapeUrl(UUID uuid) {
        return capeUrls.get(uuid.toString());
    }

    public static String getSkinUrl(UUID uuid) {
        return skinUrls.get(uuid.toString());
    }

    public static boolean isCapeCached(UUID uuid) {
        String key = uuid.toString();
        return capeUrls.containsKey(key) || noCape.contains(key);
    }

    public static boolean isSkinCached(UUID uuid) {
        String key = uuid.toString();
        return skinUrls.containsKey(key) || noSkin.contains(key);
    }

    public static void ensurePrefetched() {
        if (!fetchStarted) {
            prefetch();
        }
        try {
            fetchDone.await(2500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void captureTextures(UUID uuid, MinecraftProfileTextures textures) {
        if (textures != null) {
            baseTextures.put(uuid.toString(), textures);
        }
    }

    private static void prefetch() {
        synchronized (LOCK) {
            if (fetchStarted) return;
            fetchStarted = true;
        }
        new Thread(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                UUID uuid = mc.getGameProfile().getId();
                String nick = mc.getGameProfile().getName();
                if (nick == null || nick.isEmpty()) return;
                String cape = Oxevy.modDataManager.fetchCapeSync(nick);
                if (cape != null && !cape.isEmpty()) {
                    capeUrls.put(uuid.toString(), cape);
                } else {
                    noCape.add(uuid.toString());
                }
                String skin = Oxevy.modDataManager.fetchSkinSync(nick);
                if (skin != null && !skin.isEmpty()) {
                    skinUrls.put(uuid.toString(), skin);
                } else {
                    noSkin.add(uuid.toString());
                }
                persist();
            } catch (Exception e) {
                Oxevy.LOGGER.error("[SelfCosmetics] Failed to prefetch self cosmetics", e);
            } finally {
                fetchDone.countDown();
            }
        }, "OxevySelfCosmetics").start();
    }

    /**
     * Downloads the list of usernames with the oxevy client installed (/read).
     * Cosmetic lookups are then gated on membership in this list.
     * If the download fails the list stays unloaded and lookups fall back to
     * the old per-player behaviour so cosmetics keep working.
     */
    private static void loadOxevyUsers() {
        Oxevy.modDataManager.readStatus(users -> {
            if (users == null) return;
            oxevyNicknames.clear();
            for (ModDataManager.RemoteUser user : users) {
                if (user.name != null && !user.name.isEmpty()) {
                    oxevyNicknames.add(user.name.toLowerCase(Locale.ROOT));
                }
            }
            listLoaded = true;
            Oxevy.LOGGER.info("[SelfCosmetics] Loaded {} oxevy user(s) for cosmetic lookup",
                    oxevyNicknames.size());
        });
    }

    private static boolean isOxevyClientUser(String nick) {
        return !listLoaded || oxevyNicknames.contains(nick.toLowerCase(Locale.ROOT));
    }

    private static void startScanner() {
        if (scanner != null && !scanner.isShutdown()) return;
        scanner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OxevyNearbyCosmetics");
            t.setDaemon(true);
            return t;
        });
        scanner.scheduleWithFixedDelay(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    mc.execute(SelfCosmeticsManager::nearbyPlayerTick);
                }
            } catch (Exception ignored) {
            }
        }, 0L, SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scanner.scheduleWithFixedDelay(SelfCosmeticsManager::loadOxevyUsers,
                LIST_REFRESH_MS, LIST_REFRESH_MS, TimeUnit.MILLISECONDS);
    }

    private static void stopScanner() {
        if (scanner != null) {
            scanner.shutdownNow();
            scanner = null;
        }
    }

    private static void nearbyPlayerTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!isCosmeticsEnabled()) {
            revertAllApplied();
            return;
        }
        float check = CosmeticsModule.getCheckDistance();
        float render = CosmeticsModule.getRenderDistance();
        double checkSq = check * check;
        double renderSq = render * render;
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == null || player == mc.player) continue;
            String nick = player.getGameProfile().getName();
            if (nick == null || nick.isEmpty()) continue;
            UUID uuid = player.getUUID();
            double distSq = mc.player.distanceToSqr(player);
            if (distSq > checkSq) {
                if (renderActive.remove(uuid.toString())) {
                    revertCosmeticsToPlayer(uuid);
                }
                continue;
            }
            fetchIfNeeded(uuid, nick);
            if (distSq <= renderSq) {
                applyCosmeticsToPlayer(uuid);
            } else if (renderActive.remove(uuid.toString())) {
                revertCosmeticsToPlayer(uuid);
            }
        }
    }

    private static void fetchIfNeeded(UUID uuid, String nick) {
        String key = uuid.toString();
        if (!isOxevyClientUser(nick)) {
            return;
        }
        if (!capeUrls.containsKey(key) && !noCape.contains(key) && !capeFetching.contains(key)) {
            fetchRemoteCape(uuid, nick);
        }
        if (!skinUrls.containsKey(key) && !noSkin.contains(key) && !skinFetching.contains(key)) {
            fetchRemoteSkin(uuid, nick);
        }
    }

    private static void fetchRemoteCape(UUID uuid, String nick) {
        String key = uuid.toString();
        capeFetching.add(key);
        Oxevy.modDataManager.fetchCape(nick, capeUrl -> {
            capeFetching.remove(key);
            if (capeUrl != null && !capeUrl.isEmpty()) {
                capeUrls.put(key, capeUrl);
            } else {
                noCape.add(key);
            }
            persist();
        });
    }

    private static void fetchRemoteSkin(UUID uuid, String nick) {
        String key = uuid.toString();
        skinFetching.add(key);
        Oxevy.modDataManager.fetchSkin(nick, skinUrl -> {
            skinFetching.remove(key);
            if (skinUrl != null && !skinUrl.isEmpty()) {
                skinUrls.put(key, skinUrl);
            } else {
                noSkin.add(key);
            }
            persist();
        });
    }

    private static void applyCosmeticsToPlayer(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.execute(() -> {
            String key = uuid.toString();
            String cape = capeUrls.get(key);
            String skin = skinUrls.get(key);
            if (cape == null && skin == null) return;

            MinecraftProfileTextures base = baseTextures.get(key);
            MinecraftProfileTexture baseSkin = base != null ? base.skin() : null;
            MinecraftProfileTexture baseCape = base != null ? base.cape() : null;
            MinecraftProfileTextures textures = new MinecraftProfileTextures(
                    skin != null ? new MinecraftProfileTexture(skin, null) : baseSkin,
                    cape != null ? new MinecraftProfileTexture(cape, null) : baseCape,
                    base != null ? base.elytra() : null,
                    base != null ? base.signatureState() : SignatureState.UNSIGNED
            );

            SkinManager skinManager = mc.getSkinManager();
            CompletableFuture<PlayerSkin> future = ((SkinManagerAccessor) skinManager)
                    .invokeRegisterTextures(uuid, textures);

            PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(uuid) : null;
            if (info != null) {
                ((PlayerInfoAccessor) info).setSkinLookup(() -> future.getNow(DefaultPlayerSkin.get(uuid)));
            }
            renderActive.add(key);
        });
    }

    private static void revertCosmeticsToPlayer(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.execute(() -> {
            String key = uuid.toString();
            MinecraftProfileTextures base = baseTextures.get(key);
            if (base == null) return;
            SkinManager skinManager = mc.getSkinManager();
            CompletableFuture<PlayerSkin> future = ((SkinManagerAccessor) skinManager)
                    .invokeRegisterTextures(uuid, base);
            PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(uuid) : null;
            if (info != null) {
                ((PlayerInfoAccessor) info).setSkinLookup(() -> future.getNow(DefaultPlayerSkin.get(uuid)));
            }
        });
    }

    private static void loadCache() {
        try {
            if (!Files.exists(CACHE_FILE)) return;
            String content = Files.readString(CACHE_FILE);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            readUrlMap(obj.getAsJsonObject("capes"), capeUrls);
            readUrlMap(obj.getAsJsonObject("skins"), skinUrls);
            readSet(obj.getAsJsonArray("noCape"), noCape);
            readSet(obj.getAsJsonArray("noSkin"), noSkin);
        } catch (Exception e) {
            Oxevy.LOGGER.warn("[SelfCosmetics] Failed to load cosmetics cache", e);
        }
    }

    private static void readUrlMap(JsonObject src, Map<String, String> dst) {
        if (src == null) return;
        for (Map.Entry<String, JsonElement> e : src.entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                dst.put(e.getKey(), e.getValue().getAsString());
            }
        }
    }

    private static void readSet(JsonArray src, Set<String> dst) {
        if (src == null) return;
        for (JsonElement e : src) {
            if (e.isJsonPrimitive()) {
                dst.add(e.getAsString());
            }
        }
    }

    private static synchronized void persist() {
        try {
            Path parent = CACHE_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject obj = new JsonObject();
            obj.add("capes", GSON.toJsonTree(capeUrls));
            obj.add("skins", GSON.toJsonTree(skinUrls));
            obj.add("noCape", GSON.toJsonTree(noCape));
            obj.add("noSkin", GSON.toJsonTree(noSkin));
            Files.writeString(CACHE_FILE, obj.toString());
        } catch (Exception e) {
            Oxevy.LOGGER.warn("[SelfCosmetics] Failed to persist cosmetics cache", e);
        }
    }
}

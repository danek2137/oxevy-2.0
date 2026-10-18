package me.alpha432.oxevy.model;

import com.mojang.blaze3d.platform.NativeImage;
import me.alpha432.oxevy.cosmetics.BedrockGeometry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CosmeticModelLoader implements PreparableReloadListener {

    private static CosmeticModelLoader INSTANCE;

    // Cosmetic type identifiers
    public static final String ANGEL_WINGS = "angel_wings";
    public static final String WINGED_CAPE = "winged_cape";
    public static final String DEMON_HORNS = "demon_horns";
    public static final String DEVIL_HORNS = "devil_horns";
    public static final String QUEEN_CROWN = "queen_crown";
    public static final String CROWN = "crown";
    public static final String HEADBAND = "headband";
    public static final String HALO = "halo";

    // Resource path (jar fallback under assets/oxevy/)
    private static final String MODEL_RESOURCE_BASE = "cosmetics/models/";
    private static final String TEXTURE_RESOURCE_BASE = "textures/cosmetics/";

    /** Alias: user-facing id -> asset folder name */
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        ALIASES.put(DEVIL_HORNS, "DEMON_HORNS");
        ALIASES.put(CROWN, "QUEEN_CROWN");
    }

    private final Map<String, CosmeticModel> loadedModels = new HashMap<>();
    private final Map<String, Identifier> textureLocations = new HashMap<>();
    private final Map<String, Boolean> loadAttempted = new HashMap<>();

    /** Root of the on-disk cosmetics pack: <gameDir>/oxevy/cosmetics */
    private final Path cosmeticsRoot;

    public CosmeticModelLoader() {
        this.cosmeticsRoot = FabricLoader.getInstance().getGameDir()
                .resolve("oxevy").resolve("cosmetics");
    }

    public static CosmeticModelLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CosmeticModelLoader();
        }
        return INSTANCE;
    }

    public static void register() {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        if (resourceManager instanceof ReloadableResourceManager) {
            ((ReloadableResourceManager) resourceManager).registerReloadListener(getInstance());
        }
    }

    @Override
    public CompletableFuture<Void> reload(PreparableReloadListener.SharedState state, Executor backgroundExecutor, PreparableReloadListener.PreparationBarrier barrier, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Preparation phase (nothing heavy to do; models are parsed lazily on demand)
            } catch (Exception e) {
                me.alpha432.oxevy.Oxevy.LOGGER.error("[CosmeticModelLoader] Failed to prepare cosmetic models", e);
            }
            return null;
        }, backgroundExecutor).thenCompose(barrier::wait).thenAcceptAsync(v -> {
            try {
                // Warm the cache for anything already equipped so we fail fast / log early
                for (String id : me.alpha432.oxevy.manager.LocalCosmeticsManager.getEquipped()) {
                    getModel(id);
                }
                me.alpha432.oxevy.Oxevy.LOGGER.info(
                        "[CosmeticModelLoader] Cached {} cosmetic models (root: {})",
                        loadedModels.size(), cosmeticsRoot);
            } catch (Exception e) {
                me.alpha432.oxevy.Oxevy.LOGGER.error("[CosmeticModelLoader] Failed to load cosmetic models", e);
            }
        }, gameExecutor);
    }

    @Override
    public String getName() {
        return "Oxevy Cosmetic Models";
    }

    /** Folder name on disk / in the asset pack for a cosmetic id (upper-case). */
    public static String folderFor(String id) {
        String alias = ALIASES.get(id);
        if (alias != null) return alias;
        return id.toUpperCase(Locale.ROOT);
    }

    public CosmeticModel getModel(String id) {
        CosmeticModel existing = loadedModels.get(id);
        if (existing != null) return existing;

        // Lazy load (only try each id once to avoid hammering the disk every frame)
        if (Boolean.TRUE.equals(loadAttempted.get(id))) return null;
        loadAttempted.put(id, true);

        CosmeticModel model = loadModel(id);
        if (model != null) {
            loadedModels.put(id, model);
            me.alpha432.oxevy.Oxevy.LOGGER.info("[CosmeticModelLoader] Loaded model: {}", id);
        } else {
            me.alpha432.oxevy.Oxevy.LOGGER.warn("[CosmeticModelLoader] Could not load model: {}", id);
        }
        return model;
    }

    public Identifier getTextureLocation(String id) {
        if (textureLocations.containsKey(id)) return textureLocations.get(id);
        getModel(id); // triggers texture registration as a side effect
        return textureLocations.get(id);
    }

    public boolean hasModel(String id) {
        return getModel(id) != null;
    }

    private CosmeticModel loadModel(String id) {
        String folder = folderFor(id);

        // ---- Geometry ----
        String geometryJson = null;

        // 1) On-disk pack: <gameDir>/oxevy/cosmetics/models/<FOLDER>/geometry.steve.json
        Path diskGeometry = cosmeticsRoot.resolve("models").resolve(folder).resolve("geometry.steve.json");
        if (Files.exists(diskGeometry)) {
            try {
                geometryJson = Files.readString(diskGeometry);
            } catch (IOException e) {
                me.alpha432.oxevy.Oxevy.LOGGER.error("[CosmeticModelLoader] Failed reading {}", diskGeometry, e);
            }
        }

        // 2) Fallback: bundled jar resource under assets/oxevy/
        if (geometryJson == null) {
            Identifier modelLocation = Identifier.fromNamespaceAndPath("oxevy", MODEL_RESOURCE_BASE + folder + "/geometry.steve.json");
            try (InputStream stream = Minecraft.getInstance().getResourceManager()
                    .getResource(modelLocation).map(r -> {
                        try {
                            return r.open();
                        } catch (IOException e) {
                            return null;
                        }
                    }).orElse(null)) {
                if (stream != null) {
                    geometryJson = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                me.alpha432.oxevy.Oxevy.LOGGER.debug("[CosmeticModelLoader] No jar geometry for {} ({})", id, modelLocation);
            }
        }

        if (geometryJson == null) return null;

        // ---- Texture ----
        Identifier textureLocation = resolveTexture(id, folder);
        if (textureLocation == null) {
            me.alpha432.oxevy.Oxevy.LOGGER.warn("[CosmeticModelLoader] No texture for {}", id);
            return null;
        }

        try {
            BedrockGeometry geometry = BedrockGeometry.parse(geometryJson);
            CosmeticModel model = new CosmeticModel(geometry, textureLocation);
            textureLocations.put(id, textureLocation);
            return model;
        } catch (Exception e) {
            me.alpha432.oxevy.Oxevy.LOGGER.error("[CosmeticModelLoader] Failed to parse model: {}", id, e);
            return null;
        }
    }

    /**
     * Resolves the texture for a cosmetic, preferring the on-disk pack (registered as a dynamic
     * texture so the game's renderer can sample it) and falling back to a bundled jar resource.
     */
    private Identifier resolveTexture(String id, String folder) {
        // 1) On-disk pack: <gameDir>/oxevy/cosmetics/textures/<FOLDER>/variants/<variant>/texture.png
        Path variantsDir = cosmeticsRoot.resolve("textures").resolve(folder).resolve("variants");
        if (Files.isDirectory(variantsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(variantsDir)) {
                for (Path variant : stream) {
                    if (!Files.isDirectory(variant)) continue;
                    Path png = variant.resolve("texture.png");
                    if (!Files.exists(png)) continue;
                    return registerDynamicTexture(id, png);
                }
            } catch (IOException e) {
                me.alpha432.oxevy.Oxevy.LOGGER.error("[CosmeticModelLoader] Failed scanning {}", variantsDir, e);
            }
        }

        // 2) Fallback: bundled jar texture under assets/oxevy/textures/cosmetics/<id>.png
        String legacy = legacyTextureFile(id);
        Identifier legacyId = Identifier.fromNamespaceAndPath("oxevy", TEXTURE_RESOURCE_BASE + legacy);
        if (Minecraft.getInstance().getResourceManager().getResource(legacyId).isPresent()) {
            return legacyId;
        }
        return null;
    }

    private Identifier registerDynamicTexture(String id, Path png) {
        Identifier identifier = Identifier.fromNamespaceAndPath("oxevy", "cosmetics/" + id + ".png");
        try (InputStream in = Files.newInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            DynamicTexture texture = new DynamicTexture(() -> identifier.toString(), image);
            Minecraft.getInstance().getTextureManager().register(identifier, texture);
            return identifier;
        } catch (Exception e) {
            me.alpha432.oxevy.Oxevy.LOGGER.error("[CosmeticModelLoader] Failed registering texture {} from {}", id, png, e);
            return null;
        }
    }

    private static String legacyTextureFile(String id) {
        return switch (id) {
            case ANGEL_WINGS -> "wings.png";
            case DEMON_HORNS, DEVIL_HORNS -> "horns.png";
            case QUEEN_CROWN, CROWN -> "crown.png";
            case HEADBAND -> "headband.png";
            case HALO -> "halo.png";
            default -> id + ".png";
        };
    }

    public void close() {
        loadedModels.clear();
        textureLocations.clear();
        loadAttempted.clear();
    }
}
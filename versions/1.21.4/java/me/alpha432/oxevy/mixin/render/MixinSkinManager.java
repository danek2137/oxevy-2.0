package me.alpha432.oxevy.mixin.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.manager.SelfCosmeticsManager;
import me.alpha432.oxevy.network.OxevyUserManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(SkinManager.class)
public abstract class MixinSkinManager {

    @Unique
    private static final Map<String, String> capeUrls = new ConcurrentHashMap<>();

    @Unique
    private static boolean capesLoaded = false;

    @Unique
    private static final String DEFAULT_CAPE_URL = "https://raw.githubusercontent.com/danek123321/oxevy-assets/main/Cape.png";

    @Unique
    private static final Path SKIN_SELECTION_FILE = FabricLoader.getInstance().getGameDir().resolve("oxevy").resolve("launcher_skin.json");

    @Unique
    private static String customSkinUrl = null;

    @Unique
    private MinecraftProfileTexture currentCape;

    @Unique
    private MinecraftProfileTexture currentSkin;

    @Inject(
        method = "registerTextures(Ljava/util/UUID;Lcom/mojang/authlib/minecraft/MinecraftProfileTextures;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD")
    )
    private void onRegisterTexturesHead(UUID uuid, com.mojang.authlib.minecraft.MinecraftProfileTextures textures, CallbackInfoReturnable<CompletableFuture<PlayerSkin>> cir) {
        if (!capesLoaded) {
            loadCapes();
        }

        SelfCosmeticsManager.captureTextures(uuid, textures);

        boolean isSelf = uuid.equals(Minecraft.getInstance().getGameProfile().getId());
        if (isSelf) {
            SelfCosmeticsManager.ensurePrefetched();
        }

        String uuidStr = uuid.toString();
        boolean remoteEnabled = SelfCosmeticsManager.isCosmeticsEnabled();
        String remoteCape = remoteEnabled ? SelfCosmeticsManager.getCapeUrl(uuid) : null;
        String launcherCape = capeUrls.get(uuidStr);
        String capeUrl = remoteCape != null ? remoteCape : launcherCape;
        if (capeUrl != null) {
            currentCape = new MinecraftProfileTexture(capeUrl, null);
        } else if (OxevyUserManager.isOxevyUser(uuid)) {
            currentCape = new MinecraftProfileTexture(DEFAULT_CAPE_URL, null);
        } else if (isSelf) {
            currentCape = new MinecraftProfileTexture(DEFAULT_CAPE_URL, null);
        } else {
            currentCape = null;
        }

        String remoteSkin = remoteEnabled ? SelfCosmeticsManager.getSkinUrl(uuid) : null;
        if (remoteSkin != null) {
            currentSkin = new MinecraftProfileTexture(remoteSkin, null);
        } else if (isSelf && customSkinUrl != null) {
            currentSkin = new MinecraftProfileTexture(customSkinUrl, null);
        } else {
            currentSkin = null;
        }
    }

    @ModifyVariable(
        method = "registerTextures(Ljava/util/UUID;Lcom/mojang/authlib/minecraft/MinecraftProfileTextures;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("STORE"),
        ordinal = 1
    )
    private MinecraftProfileTexture modifyCapeTexture(MinecraftProfileTexture old) {
        if (currentCape == null) return old;
        MinecraftProfileTexture result = currentCape;
        currentCape = null;
        return result;
    }

    @ModifyVariable(
        method = "registerTextures(Ljava/util/UUID;Lcom/mojang/authlib/minecraft/MinecraftProfileTextures;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("STORE"),
        ordinal = 0
    )
    private MinecraftProfileTexture modifySkinTexture(MinecraftProfileTexture old) {
        if (currentSkin == null) return old;
        MinecraftProfileTexture result = currentSkin;
        currentSkin = null;
        return result;
    }

    @Unique
    private static void loadCapes() {
        capesLoaded = true;

        try {
            loadCustomSkin();
            Oxevy.capeManager.reloadFromLauncher();

            UUID uuid = Minecraft.getInstance().getGameProfile().getId();
            String capeUrl = Oxevy.capeManager.getActiveCapeUrl();
            if (capeUrl != null && !capeUrl.isEmpty()) {
                capeUrls.putIfAbsent(uuid.toString(), capeUrl);
            }
        } catch (Exception e) {
            Oxevy.LOGGER.error("Failed to load capes", e);
        }
    }

    @Unique
    private static void loadCustomSkin() {
        try {
            if (!Files.exists(SKIN_SELECTION_FILE)) return;
            String content = Files.readString(SKIN_SELECTION_FILE);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            String mode = obj.has("mode") ? obj.get("mode").getAsString() : "NONE";
            if ("NONE".equalsIgnoreCase(mode)) return;
            if (obj.has("url") && !obj.get("url").getAsString().isEmpty()) {
                customSkinUrl = obj.get("url").getAsString();
            }
        } catch (Exception e) {
            Oxevy.LOGGER.error("Failed to load custom skin", e);
        }
    }
}

package me.alpha432.oxevy.mixin.accessor;

import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(SkinManager.class)
public interface SkinManagerAccessor {
    @Invoker("registerTextures")
    CompletableFuture<PlayerSkin> invokeRegisterTextures(UUID uuid, MinecraftProfileTextures textures);
}

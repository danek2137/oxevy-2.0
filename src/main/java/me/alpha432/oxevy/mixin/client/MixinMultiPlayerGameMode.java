package me.alpha432.oxevy.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes crashes in MultiPlayerGameMode when player is null.
 * This can happen during world loading before the player is fully initialized.
 */
@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    /**
     * Guard ensureHasSentCarriedItem against null player.
     * This method is called every tick and accesses player.getInventory()
     * which crashes if player is null during world loading.
     */
    @Inject(method = "ensureHasSentCarriedItem", at = @At("HEAD"), cancellable = true)
    private void onEnsureHasSentCarriedItem(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        // If player is null, skip to prevent NPE
        if (player == null) {
            ci.cancel();
        }
    }

    /**
     * Guard tick method against null player.
     * The tick method may access player fields which crash if player is null.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        // If player is null, skip to prevent NPE
        if (player == null) {
            ci.cancel();
        }
    }
}
package me.alpha432.oxevy.mixin.render.gui;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.gui.BanScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class MixinPauseScreen extends Screen {
    protected MixinPauseScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        if (Oxevy.banned) {
            Minecraft.getInstance().setScreen(new BanScreen());
            ci.cancel();
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addRelogButton(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.getConnection() != null) {
            // Add relog button at the bottom of the pause menu
            int buttonWidth = 150;
            int buttonHeight = 20;
            int centerX = width / 2;
            int y = height / 4 + 144; // Below the "Quit to Menu" button

            Button relogButton = Button.builder(Component.literal("Relog"), button -> {
                // Disconnect by switching to multiplayer screen (auto-disconnects)
                mc.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(null));
            }).bounds(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build();

            this.addRenderableWidget(relogButton);
        }
    }
}

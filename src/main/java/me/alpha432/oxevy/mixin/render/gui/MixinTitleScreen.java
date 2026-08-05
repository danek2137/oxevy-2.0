package me.alpha432.oxevy.mixin.render.gui;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.gui.OxevySettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends net.minecraft.client.gui.screens.Screen {
    protected MixinTitleScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        if (Oxevy.banned) {
            Minecraft.getInstance().setScreen(new me.alpha432.oxevy.features.gui.BanScreen());
            ci.cancel();
            return;
        }
        if (Oxevy.destroyed) return;
        Minecraft.getInstance().setScreen(new me.alpha432.oxevy.features.gui.OxevyMainMenu());
        ci.cancel();
    }
}

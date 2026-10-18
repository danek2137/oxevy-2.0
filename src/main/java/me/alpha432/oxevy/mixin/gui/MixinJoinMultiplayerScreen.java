package me.alpha432.oxevy.mixin.gui;

import me.alpha432.oxevy.features.gui.AccountsScreen;
import me.alpha432.oxevy.features.gui.ProxiesScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public class MixinJoinMultiplayerScreen extends Screen {

    protected MixinJoinMultiplayerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.addRenderableWidget(
            Button.builder(Component.literal("Accounts"), button -> this.minecraft.setScreen(new AccountsScreen(this)))
                .bounds(this.width - 85, 5, 80, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.literal("Proxies"), button -> this.minecraft.setScreen(new ProxiesScreen(this)))
                .bounds(this.width - 170, 5, 80, 20)
                .build()
        );
    }
}

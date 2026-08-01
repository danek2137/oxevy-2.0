package me.alpha432.oxevy.mixin.chat;

import me.alpha432.oxevy.util.player.PlayerBadges;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay {

    @Inject(method = "getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void addBadgeToTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        String name = info.getProfile().name();
        String badge = PlayerBadges.getBadge(name);
        if (badge.isEmpty()) return;

        Component display = cir.getReturnValue();
        Component badged = Component.literal(badge + " ").append(display.copy());
        cir.setReturnValue(badged);
    }
}

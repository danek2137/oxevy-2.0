package me.alpha432.oxevy.mixin.chat;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.util.player.PlayerBadges;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay {

    private static final ResourceLocation OXEVY_ICON = ResourceLocation.fromNamespaceAndPath("oxevy", "textures/oxevy-icon.png");

    @Inject(method = "getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void addBadgeToTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        String name = info.getProfile().getName();
        String badge = PlayerBadges.getBadge(name);
        if (badge.isEmpty()) return;

        Component display = cir.getReturnValue();
        Component badged = Component.literal(badge + " ").append(display.copy());
        cir.setReturnValue(badged);
    }

    @Inject(method = "renderPingIcon(Lnet/minecraft/client/gui/GuiGraphics;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V", at = @At("HEAD"))
    private void drawOxevyIcon(GuiGraphics graphics, int columnWidth, int nameX, int y, PlayerInfo info, CallbackInfo ci) {
        Player self = Minecraft.getInstance().player;
        if (self == null) return;

        String name = info.getProfile().getName();
        if (!self.getUUID().equals(info.getProfile().getId()) && !Oxevy.oxevyUsersManager.isKnown(name)) return;

        int iconSize = 9;
        int nameDrawX = nameX + 18;
        int nameWidth = Minecraft.getInstance().font.width(Component.literal(name));
        int iconX = Math.min(nameDrawX + nameWidth + 2, nameX + columnWidth - 12);
        graphics.blit(OXEVY_ICON, iconX, y, 0f, 0f, iconSize, iconSize, iconSize, iconSize);
    }
}

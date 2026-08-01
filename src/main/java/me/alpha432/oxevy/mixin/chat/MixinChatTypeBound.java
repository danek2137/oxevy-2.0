package me.alpha432.oxevy.mixin.chat;

import me.alpha432.oxevy.util.player.PlayerBadges;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatType.Bound.class)
public class MixinChatTypeBound {

    @Inject(method = "decorate(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;", at = @At("HEAD"), cancellable = true)
    private void addBadgeToSenderName(Component content, CallbackInfoReturnable<Component> cir) {
        ChatType.Bound self = (ChatType.Bound) (Object) this;
        String senderName = self.name().getString();
        String badge = PlayerBadges.getBadge(senderName);
        if (badge.isEmpty()) return;

        Component badgedName = Component.literal(badge + " ").withStyle(self.name().getStyle()).append(self.name().copy());
        ChatType.Bound badgedBound = new ChatType.Bound(self.chatType(), badgedName, self.targetName());
        cir.setReturnValue(badgedBound.decorate(content));
    }
}

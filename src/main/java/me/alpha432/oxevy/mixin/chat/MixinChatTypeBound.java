package me.alpha432.oxevy.mixin.chat;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.client.NickChangerModule;
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
        Component sender = self.name();
        String senderName = sender.getString();
        String badge = PlayerBadges.getBadge(senderName);
        if (!badge.isEmpty()) {
            sender = Component.literal(badge + " ").withStyle(sender.getStyle()).append(sender.copy());
        }

        NickChangerModule nickChanger = Oxevy.moduleManager.getModuleByClass(NickChangerModule.class);
        if (nickChanger != null && nickChanger.chat.getValue()) {
            sender = nickChanger.replace(sender);
        }

        if (sender == self.name()) return;

        ChatType.Bound changedBound = new ChatType.Bound(self.chatType(), sender, self.targetName());
        cir.setReturnValue(changedBound.decorate(content));
    }
}

package me.alpha432.oxevy.mixin.entity;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.client.NickChangerModule;
import me.alpha432.oxevy.features.modules.movement.NoPushModule;
import me.alpha432.oxevy.features.modules.render.TrueSightModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Inject(method = "getName()Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void onGetName(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof LocalPlayer)) return;
        if (Oxevy.moduleManager == null) return;

        NickChangerModule nickChanger = Oxevy.moduleManager.getModuleByClass(NickChangerModule.class);
        if (nickChanger != null && nickChanger.nametags.getValue()) {
            cir.setReturnValue(nickChanger.replace(cir.getReturnValue()));
        }
    }

    @Inject(method = "isInvisibleTo(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("RETURN"), cancellable = true)
    private void onIsInvisibleTo(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;

        TrueSightModule trueSight = Oxevy.moduleManager.getModuleByClass(TrueSightModule.class);
        if (trueSight.isEnabled())
            cir.setReturnValue(false);
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void onPush(Entity entity, CallbackInfo ci) {
        if (Oxevy.moduleManager == null) return;
        if (!((Object) this instanceof LocalPlayer)) return;

        NoPushModule noPush = Oxevy.moduleManager.getModuleByClass(NoPushModule.class);
        if (noPush != null && noPush.isEnabled()) {
            ci.cancel();
        }
    }
}

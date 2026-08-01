package me.alpha432.oxevy.mixin.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.render.HealthTagsModule;
import me.alpha432.oxevy.util.player.PlayerBadges;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void addHealthToDisplayName(T entity, S state, float tickProgress, CallbackInfo ci) {
        if (state.nameTag == null) return;
        if (!(entity instanceof LivingEntity le)) return;

        if (entity instanceof Player player) {
            String badge = PlayerBadges.getBadge(player);
            if (!badge.isEmpty()) {
                state.nameTag = Component.literal(badge + " ").withStyle(ChatFormatting.WHITE).append(state.nameTag);
            }
        }

        HealthTagsModule healthTags = Oxevy.moduleManager.getModuleByClass(HealthTagsModule.class);
        if (healthTags != null && healthTags.isEnabled()) {
            int health = (int) le.getHealth();
            MutableComponent formattedHealth = Component.literal(" ")
                .append(Integer.toString(health))
                .withStyle(health <= 5 ? ChatFormatting.DARK_RED :
                           health <= 10 ? ChatFormatting.GOLD :
                           health <= 15 ? ChatFormatting.YELLOW :
                           ChatFormatting.GREEN);
            state.nameTag = state.nameTag.copy().append(formattedHealth);
        }
    }
}

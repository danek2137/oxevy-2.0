package me.alpha432.oxevy.mixin.accessor;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(PlayerInfo.class)
public interface PlayerInfoAccessor {
    @Accessor("skinLookup")
    void setSkinLookup(Supplier<PlayerSkin> lookup);
}

package me.alpha432.oxevy.mixin.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface MixinAbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot getHoveredSlot();
}

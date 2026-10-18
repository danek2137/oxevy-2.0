package me.alpha432.oxevy.features.modules.misc;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

public class ShulkerPreviewModule extends Module {
    public final Setting<Boolean> showInInventory = bool("InInventory", true);
    public final Setting<Boolean> showInContainers = bool("InContainers", true);

    public ShulkerPreviewModule() {
        super("ShulkerPreview", "Shows shulker box contents when hovering over them", Category.MISC);
    }

    public static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.SHULKER_BOX) return true;
        if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof ShulkerBoxBlock;
        }
        return false;
    }

    public static ItemContainerContents getShulkerContents(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
    }

    public static void copyContentsInto(ItemContainerContents contents, NonNullList<ItemStack> destination) {
        contents.copyInto(destination);
    }

    public static int getShulkerColor(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem
            && blockItem.getBlock() instanceof ShulkerBoxBlock shulkerBlock) {
            return shulkerBlock.getColor().getTextureDiffuseColor();
        }
        return -1;
    }
}

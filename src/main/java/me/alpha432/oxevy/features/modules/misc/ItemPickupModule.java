package me.alpha432.oxevy.features.modules.misc;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ItemPickupModule extends Module {
    public final Setting<Double> range = num("Range", 5.0, 1.0, 10.0);
    public final Setting<Boolean> onlyPriority = bool("OnlyPriority", false);
    public final Setting<Boolean> autoSprint = bool("AutoSprint", true);

    public ItemPickupModule() {
        super("ItemPickup", "Picks up items on the ground with priority", Category.MISC);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        AABB box = mc.player.getBoundingBox().inflate(range.getValue());
        List<ItemEntity> items = mc.level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.hasPickUpDelay());

        if (items.isEmpty()) {
            mc.player.zza = 0;
            mc.player.xxa = 0;
            return;
        }

        items.sort(Comparator.comparingDouble((ItemEntity e) -> -scoreItem(e.getItem())));

        ItemEntity target = items.get(0);
        int bestScore = scoreItem(target.getItem());

        if (onlyPriority.getValue() && bestScore <= 1) {
            mc.player.zza = 0;
            mc.player.xxa = 0;
            return;
        }

        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.5) {
            mc.player.zza = 0;
            mc.player.xxa = 0;
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rad = (float) Math.toRadians(yaw - mc.player.getYRot());
        mc.player.xxa = (float) -Math.sin(rad);
        mc.player.zza = (float) Math.cos(rad);

        if (autoSprint.getValue()) {
            mc.player.setSprinting(true);
        }

        if (mc.player.horizontalCollision && mc.player.onGround()) {
            mc.player.jumpFromGround();
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.zza = 0;
            mc.player.xxa = 0;
        }
    }

    private int scoreItem(ItemStack stack) {
        int score = 0;

        switch (stack.getRarity()) {
            case UNCOMMON -> score += 2;
            case RARE -> score += 4;
            case EPIC -> score += 6;
            default -> score += 1;
        }

        if (stack.is(Items.TOTEM_OF_UNDYING)) score += 20;
        else if (stack.is(Items.ENDER_PEARL)) score += 12;
        else if (stack.is(Items.ELYTRA)) score += 15;
        else if (stack.is(Items.SHULKER_BOX)) score += 14;
        else if (stack.is(Items.ENDER_CHEST)) score += 8;

        else if (stack.is(Items.NETHERITE_SWORD)) score += 18;
        else if (stack.is(Items.DIAMOND_SWORD)) score += 15;
        else if (stack.is(Items.NETHERITE_AXE)) score += 16;
        else if (stack.is(Items.DIAMOND_AXE)) score += 13;

        else if (stack.is(Items.NETHERITE_HELMET)) score += 14;
        else if (stack.is(Items.NETHERITE_CHESTPLATE)) score += 16;
        else if (stack.is(Items.NETHERITE_LEGGINGS)) score += 15;
        else if (stack.is(Items.NETHERITE_BOOTS)) score += 13;

        else if (stack.is(Items.DIAMOND_HELMET)) score += 11;
        else if (stack.is(Items.DIAMOND_CHESTPLATE)) score += 13;
        else if (stack.is(Items.DIAMOND_LEGGINGS)) score += 12;
        else if (stack.is(Items.DIAMOND_BOOTS)) score += 10;

        else if (stack.is(Items.GOLDEN_APPLE)) score += 10;
        else if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) score += 25;
        else if (stack.is(Items.EXPERIENCE_BOTTLE)) score += 6;
        else if (stack.is(Items.CHORUS_FRUIT)) score += 5;

        score += stack.getCount();

        return score;
    }
}

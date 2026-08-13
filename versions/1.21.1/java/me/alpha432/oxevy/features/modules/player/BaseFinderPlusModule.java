package me.alpha432.oxevy.features.modules.player;

import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BaseFinderPlusModule extends Module {

    private enum Phase {
        SENDING_RTP,
        WAITING,
        MINING,
        DONE
    }

    private Phase phase = Phase.SENDING_RTP;
    private int ticksWaited = 0;

    private boolean eating = false;
    private int eatSlot = -1;
    private boolean placingBlock = false;

    public final Setting<Integer> waitTicks = num("WaitTicks", 120, 10, 400);
    public final Setting<Integer> eatHunger = num("EatHunger", 14, 1, 20);

    public BaseFinderPlusModule() {
        super("BaseFinder+", "Automated base finder: rtp, mine down, check storage, logoff", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        phase = Phase.SENDING_RTP;
        ticksWaited = 0;
        eating = false;
        eatSlot = -1;
        placingBlock = false;
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        Command.sendMessage("BaseFinder+ started");
    }

    @Override
    public void onDisable() {
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
    }

    @Override
    public void onTick() {
        if (nullCheck()) {
            disable();
            return;
        }

        if (mc.screen != null && mc.screen.isPauseScreen()) {
            mc.setScreen(null);
        }

        switch (phase) {
            case SENDING_RTP -> {
                mc.player.connection.sendCommand("rtp eu central");
                Command.sendMessage("Sent /rtp, waiting...");
                phase = Phase.WAITING;
                ticksWaited = 0;
            }

            case WAITING -> {
                ticksWaited++;
                if (ticksWaited >= waitTicks.getValue()) {
                    Command.sendMessage("Mining down to y=-50...");
                    phase = Phase.MINING;
                }
            }

            case MINING -> {
                mc.player.setXRot(90.0f);

                if (mc.player.getY() <= -50.0) {
                    mc.options.keyAttack.setDown(false);
                    mc.options.keyUse.setDown(false);
                    eating = false;
                    placingBlock = false;
                    Command.sendMessage("Reached y=-50, repeating...");
                    phase = Phase.SENDING_RTP;
                    ticksWaited = 0;
                    return;
                }

                if (handleWaterAbove()) return;

                handleEating();

                if (!eating && !placingBlock) {
                    mc.options.keyAttack.setDown(true);
                }
            }

            case DONE -> disable();
        }
    }

    private void handleEating() {
        if (mc.player.getFoodData().getFoodLevel() > eatHunger.getValue()) {
            if (eating) {
                mc.options.keyUse.setDown(false);
                if (eatSlot != -1) {
                    mc.player.getInventory().selected = eatSlot;
                    eatSlot = -1;
                }
                eating = false;
            }
            return;
        }

        if (mc.player.isUsingItem()) {
            eating = true;
            return;
        }

        mc.options.keyAttack.setDown(false);

        int slot = findFoodSlot();
        if (slot != -1) {
            eatSlot = mc.player.getInventory().selected;
            mc.player.getInventory().selected = slot;
            mc.options.keyUse.setDown(true);
            eating = true;
        }
    }

    private boolean handleWaterAbove() {
        BlockPos head = mc.player.blockPosition().above();
        boolean hasWater = false;
        for (int dy = 0; dy <= 3; dy++) {
            if (mc.level.getBlockState(head.above(dy)).getBlock() == Blocks.WATER) {
                hasWater = true;
                break;
            }
        }
        if (!hasWater) {
            placingBlock = false;
            return false;
        }

        mc.options.keyAttack.setDown(false);
        if (mc.player.isUsingItem()) return true;
        if (placingBlock) return true;

        int slot = findBlockSlot();
        if (slot != -1) {
            mc.player.getInventory().selected = slot;
            BlockPos placePos = mc.player.blockPosition().above();
            if (mc.level.getBlockState(placePos).canBeReplaced()) {
                BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(placePos.below()), Direction.UP, placePos.below(), false
                );
                InteractionResult result = mc.gameMode.useItemOn(mc.player, mc.player.getUsedItemHand(), hit);
                if (result == InteractionResult.SUCCESS) {
                    mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSwingPacket(mc.player.getUsedItemHand()));
                }
            }
            placingBlock = true;
        }
        return true;
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    private int findFoodSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                FoodProperties food = stack.getItem().components().get(net.minecraft.core.component.DataComponents.FOOD);
                if (food != null) {
                    return i;
                }
            }
        }
        return -1;
    }

}

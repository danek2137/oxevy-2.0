package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.InteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class AutoCobwebModule extends Module {
    public final Setting<Double> range = num("Range", 4.5, 1.0, 6.0);
    public final Setting<Integer> blocksPerTick = num("BPT", 1, 1, 8);

    private int blocksPlaced = 0;

    public AutoCobwebModule() {
        super("AutoCobweb", "Traps enemies in cobwebs", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        blocksPlaced = 0;

        Player target = findTarget();
        if (target == null) return;

        InteractionHand hand = getCobwebHand();
        if (hand == null) return;

        List<BlockPos> trapPositions = getTrapPositions(target);

        for (BlockPos pos : trapPositions) {
            if (blocksPlaced >= blocksPerTick.getValue()) break;

            if (InteractionUtil.isPlaceable(pos, false)) {
                InteractionUtil.place(pos, true, hand);
                blocksPlaced++;
            }
        }
    }

    private InteractionHand getCobwebHand() {
        ItemStack mainHand = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHand = mc.player.getItemInHand(InteractionHand.OFF_HAND);

        if (mainHand.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == Blocks.COBWEB) {
            return InteractionHand.MAIN_HAND;
        }
        if (offHand.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == Blocks.COBWEB) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private Player findTarget() {
        Player closest = null;
        double closestDist = range.getValue();

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (me.alpha432.oxevy.util.entity.GhostPlayerEntity.isGhost(player)) continue;
            if (Oxevy.friendManager.isFriend(player)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist < closestDist) {
                closestDist = dist;
                closest = player;
            }
        }
        return closest;
    }

    private List<BlockPos> getTrapPositions(Player target) {
        List<BlockPos> positions = new ArrayList<>();

        BlockPos playerPos = target.blockPosition();

        positions.add(playerPos);
        positions.add(playerPos.above());

        return positions;
    }
}

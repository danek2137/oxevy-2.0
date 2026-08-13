package me.alpha432.oxevy.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    public static String getName(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    public static boolean isOpaqueFullCube(BlockPos pos) {
        BlockGetter level = mc.level;
        if (level == null) return false;
        BlockState state = level.getBlockState(pos);
        return state.isSolid() && state.isSolidRender(level, pos);
    }
}

package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class XRayModule extends Module {
    public final Setting<Float> opacity = num("Opacity", 0.0f, 0.0f, 0.99f);
    public final Setting<Boolean> onlyExposed = bool("OnlyExposed", false);
    public final Setting<String> blocks = str("Blocks",
        "minecraft:coal_ore,minecraft:iron_ore,minecraft:gold_ore,minecraft:diamond_ore," +
        "minecraft:emerald_ore,minecraft:redstone_ore,minecraft:lapis_ore," +
        "minecraft:copper_ore,minecraft:ancient_debris,minecraft:nether_quartz_ore," +
        "minecraft:deepslate_coal_ore,minecraft:deepslate_iron_ore," +
        "minecraft:deepslate_gold_ore,minecraft:deepslate_diamond_ore," +
        "minecraft:deepslate_emerald_ore,minecraft:deepslate_redstone_ore," +
        "minecraft:deepslate_lapis_ore,minecraft:deepslate_copper_ore," +
        "minecraft:chest,minecraft:ender_chest,minecraft:furnace," +
        "minecraft:spawner,minecraft:crafting_table,minecraft:anvil," +
        "minecraft:enchanting_table,minecraft:beacon,minecraft:hopper," +
        "minecraft:water,minecraft:lava,minecraft:torch,minecraft:wall_torch," +
        "minecraft:tnt,minecraft:command_block,minecraft:chain_command_block," +
        "minecraft:repeating_command_block,minecraft:nether_portal," +
        "minecraft:end_portal,minecraft:end_portal_frame");

    private ArrayList<String> oreNamesCache;
    private final ThreadLocal<BlockPos.MutableBlockPos> mutablePosForExposedCheck =
        ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    public XRayModule() {
        super("XRay", "See ores through walls", Category.RENDER);
    }

    @Override
    public void onEnable() {
        oreNamesCache = new ArrayList<>(Arrays.asList(blocks.getValue().split(",")));
        oreNamesCache.replaceAll(String::trim);
        Collections.sort(oreNamesCache);
        if (mc.options != null) mc.options.gamma().set(16.0);
        if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.gamma().set(0.5);
        if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
    }

    public Boolean shouldDrawSide(BlockState state, BlockPos pos) {
        if (!isEnabled()) return null;
        boolean visible = isVisible(state.getBlock(), pos);
        if (!visible && opacity.getValue() > 0) return null;
        return visible;
    }

    public boolean shouldHideBlockEntity(BlockEntity entity) {
        if (!isEnabled()) return false;
        BlockPos pos = entity.getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();
        return !isVisible(block, pos);
    }

    public boolean isVisible(Block block, BlockPos pos) {
        String name = BlockUtils.getName(block);
        int index = Collections.binarySearch(oreNamesCache, name);
        boolean visible = index >= 0;
        if (visible && onlyExposed.getValue() && pos != null)
            return isExposed(pos);
        return visible;
    }

    private boolean isExposed(BlockPos pos) {
        BlockPos.MutableBlockPos mutablePos = mutablePosForExposedCheck.get();
        for (Direction direction : Direction.values()) {
            if (!BlockUtils.isOpaqueFullCube(mutablePos.setWithOffset(pos, direction)))
                return true;
        }
        return false;
    }

    public boolean isOpacityMode() {
        return isEnabled() && opacity.getValue() > 0;
    }

    public float getOpacityFloat() {
        return opacity.getValue();
    }
}

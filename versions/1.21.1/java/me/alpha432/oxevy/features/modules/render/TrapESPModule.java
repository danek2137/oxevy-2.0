package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class TrapESPModule extends Module {

    public Setting<EspStyle> style = mode("Style", EspStyle.BOTH);
    public Setting<Boolean> showBoxes = bool("Boxes", true);
    public Setting<Boolean> showLines = bool("Lines", true);
    public Setting<Boolean> showTNT = bool("TNT", true);
    public Setting<Boolean> showPressurePlates = bool("PressurePlates", true);
    public Setting<Boolean> showTripwire = bool("Tripwire", true);
    public Setting<Boolean> showTrappedChests = bool("TrappedChests", true);
    public Setting<Boolean> showPistons = bool("Pistons", true);
    public Setting<Boolean> showObservers = bool("Observers", true);
    public Setting<Boolean> showDispensers = bool("Dispensers", true);
    public Setting<Boolean> showNoteBlocks = bool("NoteBlocks", true);
    public Setting<Integer> range = num("Range", 32, 8, 64);
    public Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.5f, 3f);
    public Setting<Boolean> throughWalls = bool("ThroughWalls", true);

    private static final int TNT_COLOR = 0x80FF0000;
    private static final int PRESSURE_PLATE_COLOR = 0x80FFA500;
    private static final int TRIPWIRE_COLOR = 0x80FFFF00;
    private static final int TRAPPED_CHEST_COLOR = 0x80FF3232;
    private static final int PISTON_COLOR = 0x80808080;
    private static final int OBSERVER_COLOR = 0x80404040;
    private static final int DISPENSER_COLOR = 0x80A0522D;
    private static final int NOTE_BLOCK_COLOR = 0x8000FF00;

    private final List<BlockPos> trapBlocks = new ArrayList<>();
    private final List<AABB> boxes = new ArrayList<>();

    private long lastScanTime = 0;
    private static final long SCAN_INTERVAL_MS = 200;

    public TrapESPModule() {
        super("TrapESP", "Highlights trap-related blocks through walls", Category.RENDER);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        scan();

        boolean hasBoxes = showBoxes.getValue() && style.getValue() != EspStyle.LINES;
        boolean hasLines = showLines.getValue() && style.getValue() != EspStyle.BOXES;

        if (hasBoxes) {
            for (AABB box : boxes) {
                int color = getColorForBox(box);
                RenderUtil.drawOutlinedBoxes(event.getMatrix(), List.of(box), color, throughWalls.getValue(), lineWidth.getValue());
                RenderUtil.drawSolidBoxes(event.getMatrix(), List.of(box), (color & 0x00FFFFFF) | 0x28000000, throughWalls.getValue());
            }
        }

        if (hasLines) {
            Vec3 start = RenderUtil.getTracerOrigin();
            List<Vec3> ends = new ArrayList<>(trapBlocks.size());
            List<Integer> colorsList = new ArrayList<>(trapBlocks.size());

            for (BlockPos pos : trapBlocks) {
                AABB box = new AABB(pos);
                ends.add(box.getCenter());
                colorsList.add(getColorForBlock(pos));
            }

            if (!ends.isEmpty()) {
                RenderUtil.drawTracersToPoints(event.getMatrix(), event.getDelta(), start, ends, colorsList, lineWidth.getValue(), throughWalls.getValue());
            }
        }
    }

    private void scan() {
        long now = System.currentTimeMillis();
        if (now - lastScanTime < SCAN_INTERVAL_MS) return;
        lastScanTime = now;

        trapBlocks.clear();
        boxes.clear();

        int r = range.getValue();
        int rSq = r * r;
        int minY = Math.max(mc.level.getMinBuildHeight(), (int) mc.player.getY() - r);
        int maxY = Math.min(mc.level.getMaxBuildHeight(), (int) mc.player.getY() + r);

        int cx = (int) mc.player.getX() >> 4;
        int cz = (int) mc.player.getZ() >> 4;
        int chunkR = r / 16 + 1;

        for (int dx = -chunkR; dx <= chunkR; dx++) {
            for (int dz = -chunkR; dz <= chunkR; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                if (chunk == null) continue;

                int chunkBaseX = (cx + dx) << 4;
                int chunkBaseZ = (cz + dz) << 4;

                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        int wx = chunkBaseX + bx;
                        int wz = chunkBaseZ + bz;
                        double dxTarget = wx - mc.player.getX() + 0.5;
                        double dzTarget = wz - mc.player.getZ() + 0.5;

                        for (int wy = minY; wy <= maxY; wy++) {
                            double dyTarget = wy - mc.player.getY() + 0.5;
                            if (dxTarget * dxTarget + dyTarget * dyTarget + dzTarget * dzTarget > rSq)
                                continue;

                            BlockPos pos = new BlockPos(wx, wy, wz);
                            BlockState state = mc.level.getBlockState(pos);
                            if (isTrapBlock(state)) {
                                trapBlocks.add(pos.immutable());
                                boxes.add(new AABB(pos).inflate(0.02));
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isTrapBlock(BlockState state) {
        var block = state.getBlock();
        if (block == Blocks.TNT && showTNT.getValue()) return true;
        if ((block == Blocks.STONE_PRESSURE_PLATE || block == Blocks.OAK_PRESSURE_PLATE
            || block == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE || block == Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE
            || block == Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE) && showPressurePlates.getValue()) return true;
        if ((block == Blocks.TRIPWIRE_HOOK || block == Blocks.TRIPWIRE) && showTripwire.getValue()) return true;
        if (block == Blocks.TRAPPED_CHEST && showTrappedChests.getValue()) return true;
        if ((block == Blocks.PISTON || block == Blocks.STICKY_PISTON) && showPistons.getValue()) return true;
        if (block == Blocks.OBSERVER && showObservers.getValue()) return true;
        if ((block == Blocks.DISPENSER || block == Blocks.DROPPER) && showDispensers.getValue()) return true;
        if (block == Blocks.NOTE_BLOCK && showNoteBlocks.getValue()) return true;
        return false;
    }

    private int getColorForBlock(BlockPos pos) {
        var block = mc.level.getBlockState(pos).getBlock();
        if (block == Blocks.TNT) return TNT_COLOR;
        if (block == Blocks.STONE_PRESSURE_PLATE || block == Blocks.OAK_PRESSURE_PLATE
            || block == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE || block == Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE
            || block == Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE) return PRESSURE_PLATE_COLOR;
        if (block == Blocks.TRIPWIRE_HOOK || block == Blocks.TRIPWIRE) return TRIPWIRE_COLOR;
        if (block == Blocks.TRAPPED_CHEST) return TRAPPED_CHEST_COLOR;
        if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON) return PISTON_COLOR;
        if (block == Blocks.OBSERVER) return OBSERVER_COLOR;
        if (block == Blocks.DISPENSER || block == Blocks.DROPPER) return DISPENSER_COLOR;
        if (block == Blocks.NOTE_BLOCK) return NOTE_BLOCK_COLOR;
        return 0x80FFFFFF;
    }

    private int getColorForBox(AABB box) {
        Vec3 center = box.getCenter();
        BlockPos pos = BlockPos.containing(center.x, center.y, center.z);
        return getColorForBlock(pos);
    }

    public enum EspStyle {
        BOXES,
        LINES,
        BOTH
    }
}

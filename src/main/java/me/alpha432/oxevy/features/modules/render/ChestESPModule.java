package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class ChestESPModule extends Module {
    
    public Setting<EspStyle> style = mode("Style", EspStyle.BOTH);
    public Setting<Boolean> showBoxes = bool("Boxes", true);
    public Setting<Boolean> showLines = bool("Lines", true);
    public Setting<Boolean> showChests = bool("Chests", true);
    public Setting<Boolean> showEnderChests = bool("EnderChests", true);
    public Setting<Boolean> showTrappedChests = bool("TrappedChests", true);
    public Setting<Boolean> showShulkers = bool("Shulkers", true);
    public Setting<Boolean> showBarrels = bool("Barrels", true);
    public Setting<Boolean> merge = bool("Merge", true);
    public Setting<Float> mergeGap = num("Merge Gap", 1.0f, 0.0f, 8.0f);
    public Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.5f, 3f);
    public Setting<Boolean> throughWalls = bool("ThroughWalls", true);
    public Setting<Boolean> pulseNew = bool("PulseNew", true);
    
    private static final int CHEST_COLOR = 0x80FFC864;
    private static final int ENDER_COLOR = 0x808C00FF;
    private static final int TRAPPED_COLOR = 0x80FF3232;
    private static final int BARREL_COLOR = 0x80B48C64;
    
    private static final int[] SHULKER_COLORS = {
        0x80D7D2D2, 0x80FAAF3C, 0x80EBEB32, 0x806EEB6E,
        0x8019D2EB, 0x803C46F5, 0x80E132F5, 0x80AFAFAF,
        0x80414141, 0x80FA6464, 0x80FAAF3C, 0x80EBEB32,
        0x806EEB6E, 0x803CA5E6, 0x80A05AF5, 0x80F54BD7
    };
    
    /**
     * One kind of container: where the last scan found them, and the boxes to draw
     * for them - one per container, or one per merged cluster when merging is on.
     */
    private static class Containers {
        final Map<BlockPos, AABB> found = new HashMap<>();
        final List<AABB> boxes = new ArrayList<>();
    }
    
    private final Containers chests = new Containers();
    private final Containers enderChests = new Containers();
    private final Containers trappedChests = new Containers();
    private final Containers shulkers = new Containers();
    private final Containers barrels = new Containers();
    
    /** Scratch space for {@link #group}, reused so a scan does not churn the heap. */
    private final Map<Long, List<BlockPos>> cells = new HashMap<>();
    private final Set<BlockPos> visited = new HashSet<>();
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    
    private long lastScanTime = 0;
    private static final long SCAN_INTERVAL_MS = 50;
    
    public ChestESPModule() {
        super("ChestESP", "Highlights storage containers through walls", Category.RENDER);
    }
    
    private void scan() {
        if (nullCheck()) return;
        
        long now = System.currentTimeMillis();
        if (now - lastScanTime < SCAN_INTERVAL_MS) return;
        lastScanTime = now;
        
        chests.found.clear();
        enderChests.found.clear();
        trappedChests.found.clear();
        shulkers.found.clear();
        barrels.found.clear();
        
        int cx = (int) mc.player.getX() >> 4;
        int cz = (int) mc.player.getZ() >> 4;
        int range = 48;
        int r = range / 16 + 1;
        
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                var chunk = mc.level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                if (!(chunk instanceof LevelChunk lc)) continue;
                
                for (var entry : lc.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    var be = entry.getValue();
                    var block = be.getBlockState().getBlock();
                    
                    if (block == Blocks.CHEST) {
                        if (!showChests.getValue()) continue;
                        chests.found.put(pos.immutable(), getChestBox(pos));
                    } else if (block == Blocks.ENDER_CHEST) {
                        if (!showEnderChests.getValue()) continue;
                        enderChests.found.put(pos.immutable(), getEnderChestBox(pos));
                    } else if (block == Blocks.TRAPPED_CHEST) {
                        if (!showTrappedChests.getValue()) continue;
                        trappedChests.found.put(pos.immutable(), getChestBox(pos));
                    } else if (block instanceof ShulkerBoxBlock) {
                        if (!showShulkers.getValue()) continue;
                        shulkers.found.put(pos.immutable(), getShulkerBox(pos));
                    } else if (block == Blocks.BARREL) {
                        if (!showBarrels.getValue()) continue;
                        barrels.found.put(pos.immutable(), getBarrelBox(pos));
                    }
                }
            }
        }
        
        double gap = mergeGap.getValue();
        boolean mergeBoxes = merge.getValue();
        group(chests, mergeBoxes, gap);
        group(enderChests, mergeBoxes, gap);
        group(trappedChests, mergeBoxes, gap);
        group(shulkers, mergeBoxes, gap);
        group(barrels, mergeBoxes, gap);
    }
    
    /**
     * Rebuilds one kind's draw list. With merging off that is a box per container;
     * with merging on, containers no further apart than {@code gap} collapse into a
     * single box per cluster, so a wall of fifty packed chests draws as one box
     * (and costs one draw call) instead of fifty.
     *
     * <p>Containers are bucketed into a grid whose cells are big enough that any
     * pair close enough to merge shares a cell or touches one, then each cluster is
     * flood filled - neighbours cost a handful of map lookups, not a pairwise sweep.
     */
    private void group(Containers kind, boolean mergeBoxes, double gap) {
        kind.boxes.clear();
        if (kind.found.isEmpty()) return;
        
        if (!mergeBoxes) {
            kind.boxes.addAll(kind.found.values());
            return;
        }
        
        int cell = Math.max(1, (int) Math.ceil(gap + 1.0));
        
        cells.clear();
        for (BlockPos pos : kind.found.keySet()) {
            cells.computeIfAbsent(cellKey(pos, cell), k -> new ArrayList<>()).add(pos);
        }
        
        visited.clear();
        for (Map.Entry<BlockPos, AABB> entry : kind.found.entrySet()) {
            BlockPos start = entry.getKey();
            if (!visited.add(start)) continue;
            
            AABB bounds = entry.getValue();
            queue.clear();
            queue.add(start);
            
            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                AABB currentBox = kind.found.get(current);
                int gx = Math.floorDiv(current.getX(), cell);
                int gy = Math.floorDiv(current.getY(), cell);
                int gz = Math.floorDiv(current.getZ(), cell);
                
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            List<BlockPos> neighbours = cells.get(cellKey(gx + dx, gy + dy, gz + dz));
                            if (neighbours == null) continue;
                            
                            for (BlockPos neighbour : neighbours) {
                                if (visited.contains(neighbour)) continue;
                                AABB neighbourBox = kind.found.get(neighbour);
                                if (!withinGap(currentBox, neighbourBox, gap)) continue;
                                
                                visited.add(neighbour);
                                queue.add(neighbour);
                                bounds = bounds.minmax(neighbourBox);
                            }
                        }
                    }
                }
            }
            
            kind.boxes.add(bounds);
        }
    }
    
    /** True while neither box is further away than {@code gap} on any axis. */
    private static boolean withinGap(AABB a, AABB b, double gap) {
        return a.minX - b.maxX <= gap && b.minX - a.maxX <= gap
            && a.minY - b.maxY <= gap && b.minY - a.maxY <= gap
            && a.minZ - b.maxZ <= gap && b.minZ - a.maxZ <= gap;
    }
    
    private static long cellKey(BlockPos pos, int cell) {
        return cellKey(Math.floorDiv(pos.getX(), cell), Math.floorDiv(pos.getY(), cell), Math.floorDiv(pos.getZ(), cell));
    }
    
    /** Packs a grid cell into a long; 21 bits an axis is far more than the 48 block reach needs. */
    private static long cellKey(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }
    
    @Subscribe
    public void onRender3D(Render3DEvent event) {
        scan();
        
        boolean hasBoxes = showBoxes.getValue() && style.getValue() != EspStyle.LINES;
        boolean hasLines = showLines.getValue() && style.getValue() != EspStyle.BOXES;
        
        if (hasBoxes) {
            renderGroup(event, chests.boxes, CHEST_COLOR);
            renderGroup(event, enderChests.boxes, ENDER_COLOR);
            renderGroup(event, trappedChests.boxes, TRAPPED_COLOR);
            renderGroup(event, barrels.boxes, BARREL_COLOR);
            renderShulkers(event);
        }
        
        if (hasLines) {
            List<Vec3> ends = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            
            addBoxCenters(chests.boxes, (CHEST_COLOR & 0x00FFFFFF) | 0xB4000000, ends, colors);
            addBoxCenters(enderChests.boxes, (ENDER_COLOR & 0x00FFFFFF) | 0xB4000000, ends, colors);
            addBoxCenters(trappedChests.boxes, (TRAPPED_COLOR & 0x00FFFFFF) | 0xB4000000, ends, colors);
            addBoxCenters(shulkers.boxes, 0xB4FFC8C8, ends, colors);
            addBoxCenters(barrels.boxes, (BARREL_COLOR & 0x00FFFFFF) | 0xB4000000, ends, colors);
            
            if (!ends.isEmpty()) {
                RenderUtil.drawTracersToPoints(event.getMatrix(), event.getDelta(), 
                    RenderUtil.getTracerOrigin(), ends, colors, lineWidth.getValue(), throughWalls.getValue());
            }
        }
    }
    
    /** One outline and one fill for the whole list; each of those is a single draw call. */
    private void renderGroup(Render3DEvent event, List<AABB> boxes, int color) {
        if (boxes.isEmpty()) return;
        RenderUtil.drawOutlinedBoxes(event.getMatrix(), boxes, color, throughWalls.getValue(), lineWidth.getValue());
        RenderUtil.drawSolidBoxes(event.getMatrix(), boxes, (color & 0x00FFFFFF) | 0x28000000, throughWalls.getValue());
    }
    
    /** Shulkers get a colour each, so they cannot share one batch. */
    private void renderShulkers(Render3DEvent event) {
        for (int i = 0; i < shulkers.boxes.size(); i++) {
            int color = SHULKER_COLORS[i % SHULKER_COLORS.length];
            AABB box = shulkers.boxes.get(i);
            RenderUtil.drawBox(event.getMatrix(), box, color, lineWidth.getValue(), throughWalls.getValue());
            RenderUtil.drawBoxFilled(event.getMatrix(), box, (color & 0x00FFFFFF) | 0x28000000);
        }
    }
    
    private AABB getChestBox(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), 
            pos.getX() + 1, pos.getY() + 0.875, pos.getZ() + 1);
    }
    
    private AABB getEnderChestBox(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), 
            pos.getX() + 1, pos.getY() + 0.875, pos.getZ() + 1);
    }
    
    private AABB getShulkerBox(BlockPos pos) {
        return new AABB(pos.getX() + 0.0625, pos.getY(), pos.getZ() + 0.0625,
            pos.getX() + 0.9375, pos.getY() + 0.9375, pos.getZ() + 0.9375);
    }
    
    private AABB getBarrelBox(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), 
            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }
    
    private void addBoxCenters(Collection<AABB> boxes, int color, List<Vec3> ends, List<Integer> colors) {
        for (AABB box : boxes) {
            ends.add(box.getCenter());
            colors.add(color);
        }
    }
    
    public enum EspStyle {
        BOXES,
        LINES,
        BOTH
    }
}

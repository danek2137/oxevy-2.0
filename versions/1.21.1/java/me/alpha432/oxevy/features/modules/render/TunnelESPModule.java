package me.alpha432.oxevy.features.modules.render;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TunnelESPModule extends Module {
    private static final BlockPos.MutableBlockPos BP = new BlockPos.MutableBlockPos();
    private static final Direction[] DIRECTIONS = { Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST };

    public Setting<Double> boxHeight = num("Height", 0.1, 0.0, 2.0);
    public Setting<Boolean> connected = bool("Connected", true);
    public Setting<Color> sideColor = color("SideColor", new Color(255, 175, 25, 50));
    public Setting<Color> lineColor = color("LineColor", new Color(255, 175, 25, 255));
    public Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.5f, 4.0f);

    private final Long2ObjectMap<TChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TunnelESPModule() {
        super("TunnelESP", "Highlights tunnels", Category.RENDER);
    }

    @Override
    public void onDisable() {
        chunks.clear();
    }

    private static int pack(int x, int y, int z) {
        return ((x & 0xFF) << 24) | ((y & 0xFFFF) << 8) | (z & 0xFF);
    }

    private static byte getPackedX(int p) {
        return (byte) (p >> 24 & 0xFF);
    }

    private static short getPackedY(int p) {
        return (short) (p >> 8 & 0xFFFF);
    }

    private static byte getPackedZ(int p) {
        return (byte) (p & 0xFF);
    }

    private void searchChunk(LevelChunk chunk, TChunk tChunk) {
        Context ctx = new Context();
        IntSet set = new IntOpenHashSet();

        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        int endX = chunk.getPos().getMaxBlockX();
        int endZ = chunk.getPos().getMaxBlockZ();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                int topY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

                for (short y = (short) mc.level.getMinBuildHeight(); y < topY; y++) {
                    if (isTunnel(ctx, x, y, z)) set.add(pack(x - startX, y, z - startZ));
                }
            }
        }

        IntSet positions = new IntOpenHashSet();
        for (IntIterator it = set.iterator(); it.hasNext();) {
            int packed = it.nextInt();
            byte x = getPackedX(packed);
            short y = getPackedY(packed);
            byte z = getPackedZ(packed);

            if (x == 0 || x == 15 || z == 0 || z == 15) positions.add(packed);
            else {
                boolean has = false;
                for (Direction dir : DIRECTIONS) {
                    if (set.contains(pack(x + dir.getStepX(), y, z + dir.getStepZ()))) {
                        has = true;
                        break;
                    }
                }
                if (has) positions.add(packed);
            }
        }

        tChunk.positions = positions;
    }

    private boolean isTunnel(Context ctx, int x, int y, int z) {
        if (!canWalkIn(ctx, x, y, z)) return false;

        TunnelSide s1 = getTunnelSide(ctx, x + 1, y, z);
        if (s1 == TunnelSide.PartiallyBlocked) return false;
        TunnelSide s2 = getTunnelSide(ctx, x - 1, y, z);
        if (s2 == TunnelSide.PartiallyBlocked) return false;
        TunnelSide s3 = getTunnelSide(ctx, x, y, z + 1);
        if (s3 == TunnelSide.PartiallyBlocked) return false;
        TunnelSide s4 = getTunnelSide(ctx, x, y, z - 1);
        if (s4 == TunnelSide.PartiallyBlocked) return false;

        return (s1 == TunnelSide.Walkable && s2 == TunnelSide.Walkable && s3 == TunnelSide.FullyBlocked && s4 == TunnelSide.FullyBlocked)
            || (s1 == TunnelSide.FullyBlocked && s2 == TunnelSide.FullyBlocked && s3 == TunnelSide.Walkable && s4 == TunnelSide.Walkable);
    }

    private TunnelSide getTunnelSide(Context ctx, int x, int y, int z) {
        if (canWalkIn(ctx, x, y, z)) return TunnelSide.Walkable;
        if (!canWalkThrough(ctx, x, y, z) && !canWalkThrough(ctx, x, y + 1, z)) return TunnelSide.FullyBlocked;
        return TunnelSide.PartiallyBlocked;
    }

    private boolean canWalkOn(Context ctx, int x, int y, int z) {
        BlockState state = ctx.get(x, y, z);
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        return !state.getCollisionShape(mc.level, BP.set(x, y, z)).isEmpty();
    }

    private boolean canWalkThrough(Context ctx, int x, int y, int z) {
        BlockState state = ctx.get(x, y, z);
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return false;
        return state.getCollisionShape(mc.level, BP.set(x, y, z)).isEmpty();
    }

    private boolean canWalkIn(Context ctx, int x, int y, int z) {
        if (!canWalkOn(ctx, x, y - 1, z)) return false;
        if (!canWalkThrough(ctx, x, y, z)) return false;
        if (canWalkThrough(ctx, x, y + 2, z)) return false;
        return canWalkThrough(ctx, x, y + 1, z);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        synchronized (chunks) {
            for (TChunk tChunk : chunks.values()) tChunk.marked = false;
            int added = 0;

            for (LevelChunk chunk : getLoadedChunks()) {
                long key = ChunkPos.asLong(chunk.getPos().x, chunk.getPos().z);
                if (chunks.containsKey(key)) chunks.get(key).marked = true;
                else if (added < 48) {
                    TChunk tChunk = new TChunk(chunk.getPos().x, chunk.getPos().z);
                    chunks.put(tChunk.getKey(), tChunk);
                    final LevelChunk fChunk = chunk;
                    executor.execute(() -> searchChunk(fChunk, tChunk));
                    added++;
                }
            }

            chunks.values().removeIf(tChunk -> !tChunk.marked);
        }
    }

    private List<LevelChunk> getLoadedChunks() {
        List<LevelChunk> list = new ArrayList<>();
        int viewDist = mc.options.getEffectiveRenderDistance();
        ChunkPos center = mc.player.chunkPosition();
        for (int x = center.x - viewDist; x <= center.x + viewDist; x++) {
            for (int z = center.z - viewDist; z <= center.z + viewDist; z++) {
                LevelChunk chunk = (LevelChunk) mc.level.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) list.add(chunk);
            }
        }
        return list;
    }

    private boolean chunkContains(TChunk chunk, int x, int y, int z) {
        int key;

        if (x == -1) {
            chunk = chunks.get(ChunkPos.asLong(chunk.x - 1, chunk.z));
            key = pack(15, y, z);
        } else if (x == 16) {
            chunk = chunks.get(ChunkPos.asLong(chunk.x + 1, chunk.z));
            key = pack(0, y, z);
        } else if (z == -1) {
            chunk = chunks.get(ChunkPos.asLong(chunk.x, chunk.z - 1));
            key = pack(x, y, 15);
        } else if (z == 16) {
            chunk = chunks.get(ChunkPos.asLong(chunk.x, chunk.z + 1));
            key = pack(x, y, 0);
        } else key = pack(x, y, z);

        return chunk != null && chunk.positions != null && chunk.positions.contains(key);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        synchronized (chunks) {
            for (TChunk chunk : chunks.values()) {
                chunk.render(event);
            }
        }
    }

    private class TChunk {
        private final int x, z;
        public IntSet positions;
        public boolean marked;

        public TChunk(int x, int z) {
            this.x = x;
            this.z = z;
            this.marked = true;
        }

        public void render(Render3DEvent event) {
            if (positions == null) return;

            Color side = sideColor.getValue();
            Color line = lineColor.getValue();
            double h = boxHeight.getValue();

            for (IntIterator it = positions.iterator(); it.hasNext();) {
                int pos = it.nextInt();
                int bx = getPackedX(pos);
                int by = getPackedY(pos);
                int bz = getPackedZ(pos);

                int worldX = bx + this.x * 16;
                int worldZ = bz + this.z * 16;

                AABB box = new AABB(worldX, by, worldZ, worldX + 1, by + h, worldZ + 1);
                RenderUtil.drawBox(event.getMatrix(), box, line, lineWidth.getValue(), true);
                RenderUtil.drawBoxFilled(event.getMatrix(), box, side);
            }
        }

        public long getKey() {
            return ChunkPos.asLong(x, z);
        }
    }

    private static class Context {
        private final Level world;
        private LevelChunk lastChunk;

        public Context() {
            this.world = mc.level;
        }

        public BlockState get(int x, int y, int z) {
            if (world.isOutsideBuildHeight(y)) return Blocks.AIR.defaultBlockState();

            int cx = x >> 4;
            int cz = z >> 4;
            LevelChunk chunk;
            if (lastChunk != null && lastChunk.getPos().x == cx && lastChunk.getPos().z == cz) chunk = lastChunk;
            else chunk = (LevelChunk) world.getChunk(cx, cz, ChunkStatus.FULL, false);

            if (chunk == null) return Blocks.AIR.defaultBlockState();

            LevelChunkSection section = chunk.getSections()[chunk.getSectionIndex(y)];
            if (section == null) return Blocks.AIR.defaultBlockState();

            lastChunk = chunk;
            return section.getBlockState(x & 15, y & 15, z & 15);
        }
    }

    private enum TunnelSide {
        Walkable,
        PartiallyBlocked,
        FullyBlocked
    }
}

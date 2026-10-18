package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Guesses which chunks hold a base by scoring them against a set of telltale blocks.
 * <p>
 * Results are reported per chunk (never per block): every loaded chunk inside {@link #range} is
 * scanned for evidence blocks and a chunk is only flagged once it collects {@link #minHits} of them.
 * Scoring is cached per chunk and refreshed in a round-robin so a single tick never scans everything.
 */
public class BaseFinderModule extends Module {
    private static final double CHUNK_FOOTPRINT_HEIGHT = 0.04;

    public final Setting<Integer> range = num("Range", 128, 16, 512);
    public final Setting<Integer> verticalRange = num("VerticalRange", 128, 16, 384);
    public final Setting<Integer> minHits = num("MinHits", 3, 1, 128);
    public final Setting<Integer> maxChunks = num("MaxChunks", 48, 1, 512);
    public final Setting<Integer> scanBudget = num("ScanBudget", 2, 1, 32);
    public final Setting<Integer> rescanTicks = num("RescanTicks", 100, 20, 1200);

    // what counts as evidence
    public final Setting<Boolean> amethyst = bool("Amethyst", true);
    public final Setting<Boolean> budding = bool("Budding", true);
    public final Setting<Integer> minGrowthStage = num("MinGrowthStage", 3, 0, 3);
    public final Setting<Boolean> lights = bool("Light", true);
    public final Setting<Integer> minLightLevel = num("MinLightLevel", 15, 0, 15);
    public final Setting<Boolean> storage = bool("Storage", true);
    public final Setting<Boolean> utility = bool("Utility", true);
    public final Setting<Boolean> natural = bool("Natural", false);
    public final Setting<Boolean> naturalOnly = bool("NaturalOnly", false);

    public final Setting<Boolean> announce = bool("Announce", true);
    public final Setting<Boolean> tracers = bool("Tracers", true);
    public final Setting<Boolean> boxes = bool("Boxes", true);
    public final Setting<Boolean> fill = bool("Fill", false);
    public final Setting<Boolean> throughWalls = bool("ThroughWalls", true);
    public final Setting<Integer> red = num("Red", 255, 0, 255);
    public final Setting<Integer> green = num("Green", 0, 0, 255);
    public final Setting<Integer> blue = num("Blue", 0, 0, 255);
    public final Setting<Integer> alpha = num("Alpha", 100, 0, 255);
    public final Setting<Integer> outlineAlpha = num("OutlineAlpha", 255, 0, 255);
    public final Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.1f, 5.0f);

    /** A chunk the module believes holds a base. */
    public static final class ChunkHit {
        public final int x;
        public final int z;
        public int hits;
        private long lastScan = Long.MIN_VALUE / 2;

        ChunkHit(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public long key() {
            return ChunkPos.asLong(this.x, this.z);
        }

        public double centerX() {
            return this.x * 16.0 + 8.0;
        }

        public double centerZ() {
            return this.z * 16.0 + 8.0;
        }

        public Vec3 center(int minY, int maxY) {
            return new Vec3(this.centerX(), (minY + maxY) / 2.0, this.centerZ());
        }
    }

    /** Amethyst clusters and the budding block they grow from. */
    private static final Set<Block> AMETHYST_BLOCKS = Set.of(
            Blocks.AMETHYST_CLUSTER,
            Blocks.BUDDING_AMETHYST
    );

    /** Blocks that emit light, the light block being the classic base tell. */
    private static final Set<Block> LIGHT_BLOCKS = Set.of(
            Blocks.LIGHT,
            Blocks.GLOWSTONE,
            Blocks.SEA_LANTERN,
            Blocks.SHROOMLIGHT,
            Blocks.LANTERN,
            Blocks.SOUL_LANTERN,
            Blocks.REDSTONE_LAMP,
            Blocks.JACK_O_LANTERN,
            Blocks.PEARLESCENT_FROGLIGHT,
            Blocks.VERDANT_FROGLIGHT,
            Blocks.OCHRE_FROGLIGHT,
            Blocks.GLOW_LICHEN,
            Blocks.SCULK_SHRIEKER
    );

    /** Containers, furnaces and anything else that only exists inside a base. */
    private static final Set<Block> STORAGE_BLOCKS = Set.of(
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.ENDER_CHEST,
            Blocks.BARREL,
            Blocks.HOPPER,
            Blocks.FURNACE,
            Blocks.BLAST_FURNACE,
            Blocks.SMOKER,
            Blocks.BREWING_STAND,
            Blocks.DROPPER,
            Blocks.DISPENSER
    );

    /** Workstations and decorations that give a base away. */
    private static final Set<Block> UTILITY_BLOCKS = Set.of(
            Blocks.CRAFTING_TABLE,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL,
            Blocks.ENCHANTING_TABLE,
            Blocks.SMITHING_TABLE,
            Blocks.GRINDSTONE,
            Blocks.STONECUTTER,
            Blocks.CARTOGRAPHY_TABLE,
            Blocks.FLETCHING_TABLE,
            Blocks.LOOM,
            Blocks.COMPOSTER,
            Blocks.CAULDRON,
            Blocks.LECTERN,
            Blocks.JUKEBOX,
            Blocks.BOOKSHELF,
            Blocks.CHISELED_BOOKSHELF,
            Blocks.DECORATED_POT,
            Blocks.BEEHIVE,
            Blocks.BEE_NEST,
            Blocks.SPAWNER,
            Blocks.BEACON,
            Blocks.LIGHTNING_ROD,
            Blocks.NOTE_BLOCK
    );

    /** Blocks world generation spawns on its own, never something a player placed. */
    private static final Set<Block> NATURAL_BLOCKS = Set.of(
            Blocks.AMETHYST_CLUSTER,
            Blocks.BUDDING_AMETHYST,
            Blocks.GLOWSTONE,
            Blocks.GLOW_LICHEN,
            Blocks.SHROOMLIGHT,
            Blocks.PEARLESCENT_FROGLIGHT,
            Blocks.VERDANT_FROGLIGHT,
            Blocks.OCHRE_FROGLIGHT,
            Blocks.SCULK_SHRIEKER,
            Blocks.DRIPSTONE_BLOCK,
            Blocks.POINTED_DRIPSTONE
    );

    /** Minecraft renamed the section accessors, so they are resolved once by name. */
    private static final Method MIN_SECTION_Y = findMethod("getMinSectionY", "getMinSection");

    private final Map<Long, ChunkHit> cache = new HashMap<>();
    private final List<Long> loadedChunks = new ArrayList<>();
    private final List<ChunkHit> results = new ArrayList<>();
    private final Set<Long> announcedChunks = new HashSet<>();
    private final Set<Long> resultKeys = new HashSet<>();
    private final Set<Block> candidates = new HashSet<>();
    private int scanCursor;
    private long tickCounter;
    private int targetSignature = Integer.MIN_VALUE;
    private int worldMinY;
    private int worldMaxY = 384;

    public BaseFinderModule() {
        super("BaseFinder", "Guesses which chunks hold a base by scoring telltale blocks", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        int signature = buildSignature();
        if (signature != this.targetSignature) {
            this.targetSignature = signature;
            refreshCandidates();
            this.cache.clear();
        }

        int cx = (int) Math.floor(mc.player.getX()) >> 4;
        int cz = (int) Math.floor(mc.player.getZ()) >> 4;
        int r = range.getValue() / 16 + 1;
        updateWorldHeight(cx, cz);

        loadedChunks.clear();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int chunkX = cx + dx;
                int chunkZ = cz + dz;
                if (!(mc.level.getChunkSource().getChunk(chunkX, chunkZ, false) instanceof LevelChunk)) continue;
                long key = ChunkPos.asLong(chunkX, chunkZ);
                loadedChunks.add(key);
                cache.computeIfAbsent(key, k -> new ChunkHit(chunkX, chunkZ));
            }
        }
        cache.keySet().retainAll(loadedChunks);

        scanQueuedChunks();
        collectResults();

        if (announce.getValue()) {
            resultKeys.clear();
            for (ChunkHit hit : results) {
                resultKeys.add(hit.key());
            }
            for (ChunkHit hit : results) {
                if (!announcedChunks.add(hit.key())) continue;
                Command.sendMessage(ChatFormatting.AQUA + "Base chunk " + ChatFormatting.WHITE
                        + hit.x + ", " + hit.z + ChatFormatting.GRAY + " (" + hit.hits + " hits)");
            }
            announcedChunks.retainAll(resultKeys);
        } else {
            announcedChunks.clear();
        }
    }

    /** Walks the loaded chunks round-robin, rescanning at most {@link #scanBudget} of them per tick. */
    private void scanQueuedChunks() {
        int size = loadedChunks.size();
        if (size == 0 || candidates.isEmpty()) return;

        long now = this.tickCounter++;
        int budget = scanBudget.getValue();
        long rescanAfter = rescanTicks.getValue();
        int examined = 0;

        for (int i = 0; i < size && budget > 0; i++) {
            int index = Math.floorMod(this.scanCursor + i, size);
            long key = loadedChunks.get(index);
            ChunkHit hit = cache.get(key);
            if (hit == null || now - hit.lastScan < rescanAfter) continue;

            LevelChunk chunk = mc.level.getChunkSource().getChunk(hit.x, hit.z, false);
            if (chunk != null) {
                hit.hits = scanChunk(chunk);
            }
            hit.lastScan = now;
            budget--;
            examined = i + 1;
        }

        this.scanCursor = Math.floorMod(this.scanCursor + Math.max(1, examined), size);
    }

    /**
     * Derives the world height from the chunk itself. The accessors that report the build height
     * were renamed in newer Minecraft versions, so they are looked up by name with the pre-rename
     * name as fallback. Every dimension is section aligned, so section 0 starts at a multiple of 16.
     */
    private void updateWorldHeight(int cx, int cz) {
        LevelChunk chunk = mc.level.getChunk(cx, cz);
        if (chunk == null) return;

        int minSection;
        try {
            minSection = MIN_SECTION_Y == null ? 0 : (int) MIN_SECTION_Y.invoke(chunk);
        } catch (ReflectiveOperationException e) {
            minSection = 0;
        }
        int sections = chunk.getSections().length;
        this.worldMinY = minSection * 16;
        this.worldMaxY = (minSection + sections) * 16;
    }

    private static Method findMethod(String... names) {
        for (String name : names) {
            try {
                return LevelChunk.class.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // accessor only exists on newer Minecraft versions
            }
        }
        return null;
    }

    /**
     * A player flying above or below the world would otherwise produce an empty section range (and
     * an inverted render box), so the eye level is pulled back into the dimension first.
     */
    private int eyeLevel() {
        int py = (int) Math.floor(mc.player.getY());
        return Math.max(worldMinY, Math.min(worldMaxY - 1, py));
    }

    private int scanChunk(LevelChunk chunk) {
        int span = verticalRange.getValue();
        int py = eyeLevel();

        // getSection() is indexed relative to the bottom of the chunk, not by absolute section Y,
        // so the player's span has to be shifted by the chunk's lowest section first.
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = worldMinY >> 4;
        int firstIndex = Math.max(0, ((py - span) >> 4) - minSectionY);
        int lastIndex = Math.min(sections.length - 1, ((py + span) >> 4) - minSectionY);

        int hits = 0;
        for (int index = firstIndex; index <= lastIndex; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!matches(section.getBlockState(x, y, z))) continue;
                        hits++;
                    }
                }
            }
        }
        return hits;
    }

    /**
     * Collects every block worth a lookup into one set, so the scan loop only ever does a single
     * hash lookup before bailing out on the overwhelming majority of blocks.
     */
    private void refreshCandidates() {
        candidates.clear();
        if (!anyCategoryEnabled()) return;
        addCandidates(AMETHYST_BLOCKS, amethyst, budding);
        addCandidates(LIGHT_BLOCKS, lights, lights);
        addCandidates(STORAGE_BLOCKS, storage, storage);
        addCandidates(UTILITY_BLOCKS, utility, utility);
        if (natural.getValue() || naturalOnly.getValue()) candidates.addAll(NATURAL_BLOCKS);
        if (lightAllowed()) candidates.add(Blocks.LIGHT);
    }

    private boolean anyCategoryEnabled() {
        return amethyst.getValue() || budding.getValue() || lights.getValue()
                || storage.getValue() || utility.getValue() || natural.getValue();
    }

    private void addCandidates(Set<Block> blocks, Setting<Boolean> first, Setting<Boolean> second) {
        if (first.getValue() || second.getValue()) candidates.addAll(blocks);
    }

    private boolean matches(BlockState state) {
        Block block = state.getBlock();
        if (!candidates.contains(block)) {
            // coloured shulker boxes, beds and signs are all subclasses of these three
            if (storage.getValue() && (block instanceof ShulkerBoxBlock || block instanceof BedBlock)) {
                return allowed(storage, block);
            }
            if (utility.getValue() && block instanceof SignBlock) {
                return allowed(utility, block);
            }
            return false;
        }

        if (AMETHYST_BLOCKS.contains(block)) {
            if (block == Blocks.AMETHYST_CLUSTER) {
                return allowed(amethyst, block) && grownEnough(state);
            }
            return allowed(budding, block);
        }
        if (block == Blocks.LIGHT) {
            return lightAllowed() && state.getValue(LightBlock.LEVEL) >= minLightLevel.getValue();
        }
        if (LIGHT_BLOCKS.contains(block)) return allowed(lights, block);
        if (STORAGE_BLOCKS.contains(block)) return allowed(storage, block);
        if (UTILITY_BLOCKS.contains(block)) return allowed(utility, block);
        return allowed(natural, block);
    }

    /** The light block only exists because a player placed it, so NaturalOnly always excludes it. */
    private boolean lightAllowed() {
        return lights.getValue() && !naturalOnly.getValue();
    }

    /**
     * NaturalOnly narrows the whole scan down to blocks world generation creates on its own, so the
     * category toggles stop mattering: a block counts purely by being naturally generated.
     */
    private boolean allowed(Setting<Boolean> category, Block block) {
        if (naturalOnly.getValue()) return anyCategoryEnabled() && NATURAL_BLOCKS.contains(block);
        return category.getValue();
    }

    /**
     * Keeps only grown clusters. Minecraft 1.21.11 dropped the age property from amethyst clusters
     * (there is a single cluster model now), so this only filters on versions that still have it.
     */
    private boolean grownEnough(BlockState state) {
        int min = minGrowthStage.getValue();
        if (min <= 0) return true;
        if (!state.hasProperty(BlockStateProperties.AGE_4)) return true;
        return state.getValue(BlockStateProperties.AGE_4) >= min;
    }

    private void collectResults() {
        results.clear();
        double rangeSq = (double) range.getValue() * range.getValue();
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        for (ChunkHit hit : cache.values()) {
            if (hit.hits < minHits.getValue()) continue;
            double dx = hit.centerX() - px;
            double dz = hit.centerZ() - pz;
            if (dx * dx + dz * dz > rangeSq) continue;
            results.add(hit);
        }

        results.sort((a, b) -> {
            if (a.hits != b.hits) return Integer.compare(b.hits, a.hits);
            double da = Math.pow(a.centerX() - px, 2) + Math.pow(a.centerZ() - pz, 2);
            double db = Math.pow(b.centerX() - px, 2) + Math.pow(b.centerZ() - pz, 2);
            return Double.compare(da, db);
        });

        int max = maxChunks.getValue();
        while (results.size() > max) {
            announcedChunks.remove(results.remove(results.size() - 1).key());
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (nullCheck() || results.isEmpty()) return;

        int py = eyeLevel();
        int span = verticalRange.getValue();
        int minY = Math.max(this.worldMinY, py - span);
        int maxY = Math.min(this.worldMaxY, py + span);

        Color color = new Color(red.getValue(), green.getValue(), blue.getValue(), alpha.getValue());
        Color outlineColor = new Color(red.getValue(), green.getValue(), blue.getValue(), outlineAlpha.getValue());

        for (ChunkHit hit : results) {
            AABB box = new AABB(
                    hit.x * 16,
                    minY,
                    hit.z * 16,
                    hit.x * 16 + 16,
                    Math.min(maxY, minY + CHUNK_FOOTPRINT_HEIGHT),
                    hit.z * 16 + 16
            );
            if (fill.getValue()) {
                RenderUtil.drawBoxFilled(event.getMatrix(), box, color);
            }
            if (boxes.getValue()) {
                RenderUtil.drawBox(event.getMatrix(), box, outlineColor, lineWidth.getValue(), throughWalls.getValue());
            }
        }

        if (tracers.getValue()) {
            List<Vec3> ends = new ArrayList<>(results.size());
            for (ChunkHit hit : results) {
                ends.add(new Vec3(hit.centerX(), minY, hit.centerZ()));
            }
            RenderUtil.drawTracers(event.getMatrix(), event.getDelta(), ends, outlineColor.getRGB(), lineWidth.getValue(), true);
        }
    }

    @Override
    public String getDisplayInfo() {
        return results.size() + " chunks";
    }

    private int buildSignature() {
        int sig = 17;
        sig = sig * 31 + (amethyst.getValue() ? 1 : 0);
        sig = sig * 31 + (budding.getValue() ? 1 : 0);
        sig = sig * 31 + minGrowthStage.getValue();
        sig = sig * 31 + (lights.getValue() ? 1 : 0);
        sig = sig * 31 + minLightLevel.getValue();
        sig = sig * 31 + (storage.getValue() ? 1 : 0);
        sig = sig * 31 + (utility.getValue() ? 1 : 0);
        sig = sig * 31 + (natural.getValue() ? 1 : 0);
        sig = sig * 31 + (naturalOnly.getValue() ? 1 : 0);
        sig = sig * 31 + verticalRange.getValue();
        return sig;
    }

    @Override
    public void onDisable() {
        cache.clear();
        results.clear();
        loadedChunks.clear();
        announcedChunks.clear();
        scanCursor = 0;
    }
}

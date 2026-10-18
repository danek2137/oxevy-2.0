package com.oxevy.safemod.music;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Reads the shared-memory region written by {@code liboxevy-music} native
 * bridge. The layout matches the C++ {@code OxevyMusicHeader} struct in
 * {@code src/main/native/music/oxevy_music_protocol.h}.
 *
 * <p>Offsets (bytes) are fixed so the Java reader never depends on the C++
 * struct's compiler layout:
 * <pre>
 *   +0   magic      uint32   = 0x4556584F ('OXEV')
 *   +4   version    uint32
 *   +8   struct_size uint32
 *   +12  reserved   uint32
 *   +16  sequence   uint64   bumped by writer
 *   +24  updated_ms uint64
 *   +32  has_track  int32
 *   +36  playing    int32
 *   +40  position_us int64
 *   +48  length_us  int64
 *   +56  artist     char[512]
 *   +568 title      char[512]
 *   +1080 status    char[64]
 *   +1144 player_id char[64]
 *   +1208 art_url   char[1024]
 * </pre>
 */
public final class MusicSharedMemory {

    private static final int MAGIC = 0x4556584F;

    // Offsets
    private static final int O_MAGIC = 0;
    private static final int O_VERSION = 4;
    private static final int O_SEQUENCE = 16;
    private static final int O_UPDATED_MS = 24;
    private static final int O_HAS_TRACK = 32;
    private static final int O_PLAYING = 36;
    private static final int O_POSITION_US = 40;
    private static final int O_LENGTH_US = 48;
    private static final int O_ARTIST = 56;
    private static final int ARTIST_LEN = 512;
    private static final int O_TITLE = 568;
    private static final int TITLE_LEN = 512;
    private static final int O_STATUS = 1080;
    private static final int STATUS_LEN = 64;
    private static final int O_PLAYER = 1144;
    private static final int PLAYER_LEN = 64;
    private static final int O_ART_URL = 1208;
    private static final int ART_URL_LEN = 1024;
    private static final int HEADER_SIZE = 2232;

    /** Result of reading one snapshot of the shared region. */
    public static final class Snapshot {
        public boolean valid;
        public boolean hasTrack;
        public boolean playing;
        public long positionUs;
        public long lengthUs;
        public String artist = "";
        public String title = "";
        public String status = "";
        public String player = "";
        public String artUrl = "";
    }

    private MusicSharedMemory() {
    }

    private static Path resolvePath() {
        try {
            if (NativeMusicLoader.isLoaded()) {
                String p = NativeMusicLoader.getShmFilePath();
                if (p != null && !p.isEmpty()) {
                    Path path = Path.of(p);
                    if (Files.exists(path)) return path;
                }
            }
        } catch (Throwable ignored) {
            // fall through to platform default
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Path.of(System.getProperty("java.io.tmpdir"), "oxevy-music.shm");
        }
        // Linux / macOS POSIX shm mount points.
        for (String base : new String[]{"/dev/shm", "/tmp"}) {
            Path candidate = Path.of(base, "oxevy-music");
            if (Files.exists(candidate)) return candidate;
        }
        return Path.of("/dev/shm/oxevy-music");
    }

    /** Read the current state of the shared region. Never throws. */
    public static Snapshot snapshot() {
        Snapshot snap = new Snapshot();
        Path path = resolvePath();
        if (path == null || !Files.exists(path)) return snap;

        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = Math.min(HEADER_SIZE, ch.size());
            if (size < O_HAS_TRACK + 4) return snap;
            MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, size);
            // The native writer stores POD fields in native (little-endian)
            // byte order; x86/ARM are little-endian in practice.
            buf.order(ByteOrder.LITTLE_ENDIAN);

            if (buf.getInt(O_MAGIC) != MAGIC) return snap;
            snap.valid = true;

            long readSeq = buf.getLong(O_SEQUENCE);
            if (readSeq < 0) return snap;

            snap.hasTrack = buf.getInt(O_HAS_TRACK) != 0;
            snap.playing = buf.getInt(O_PLAYING) != 0;
            snap.positionUs = buf.getLong(O_POSITION_US);
            snap.lengthUs = buf.getLong(O_LENGTH_US);
            snap.artist = readCString(buf, O_ARTIST, ARTIST_LEN);
            snap.title = readCString(buf, O_TITLE, TITLE_LEN);
            snap.status = readCString(buf, O_STATUS, STATUS_LEN);
            snap.player = readCString(buf, O_PLAYER, PLAYER_LEN);
            snap.artUrl = readCString(buf, O_ART_URL, ART_URL_LEN);
        } catch (IOException | RuntimeException e) {
            // Region not ready yet; return an empty snapshot.
        }
        return snap;
    }

    private static String readCString(ByteBuffer buf, int offset, int max) {
        int limit = Math.min(offset + max, buf.limit());
        int end = offset;
        while (end < limit && buf.get(end) != 0) end++;
        if (end == offset) return "";
        byte[] bytes = new byte[end - offset];
        int pos = buf.position();
        buf.position(offset);
        buf.get(bytes);
        buf.position(pos);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

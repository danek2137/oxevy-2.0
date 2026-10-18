/*
 * oxevy native music bridge - shared memory protocol.
 *
 * A small C++ shared library (liboxevy-music.so / .dll / .dylib) reads the
 * system media player's now-playing info (MPRIS on Linux, Media Transport
 * Controls on Windows, AppleScript on macOS) and writes it into a block of
 * process-shared memory. The Java mod maps the same region and renders it in
 * the MusicPlayer HUD (title / artist / progress / album-art URL).
 *
 * Layout is intentionally externalized so the Java reader does not depend on
 * the C++ struct layout: it reads fixed offsets from a mapped ByteBuffer.
 * All string fields are UTF-8 and NUL-terminated within their buffers.
 *
 * POSIX shared memory (Linux/macOS) name:  /oxevy-music
 * Windows named shared memory name:        oxevy-music (Global\ prefix on wow64 arch mismatch)
 *
 * Numeric fields are stored in NATIVE (little-endian) byte order; the Java
 * reader (MusicSharedMemory) sets ByteOrder.LITTLE_ENDIAN accordingly.
 */

#ifndef OXEVY_MUSIC_PROTOCOL_H
#define OXEVY_MUSIC_PROTOCOL_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define OXEVY_MUSIC_SHM_NAME  "oxevy-music"

#define OXEVY_MUSIC_MAGIC     0x4556584Fu  /* 'OXEV' little-endian + 0 ('O','X','E','V') */
#define OXEVY_MUSIC_VERSION   1

#define OXEVY_MUSIC_MAX_TEXT  512   /* artist / title bytes (UTF-8) */
#define OXEVY_MUSIC_MAX_STATUS 64
#define OXEVY_MUSIC_MAX_PLAYER 64
#define OXEVY_MUSIC_MAX_ARTURL 1024

/* The first 6 fields must not move; the Java reader hard-codes these offsets. */
struct OxevyMusicHeader {
    uint32_t magic;        /* +0  OXEVY_MUSIC_MAGIC */
    uint32_t version;      /* +4  OXEVY_MUSIC_VERSION */
    uint32_t struct_size;  /* +8  bytes (for forward-compat) */
    uint32_t reserved;     /* +12 */
    uint64_t sequence;     /* +16 bumped by the writer after each commit */
    uint64_t updated_ms;   /* +24 wall-clock ms of last update */
    int32_t  has_track;    /* +32 1 when a track is present */
    int32_t  playing;      /* +36 1 when Playing, 0 otherwise */
    int64_t  position_us;  /* +40 */
    int64_t  length_us;    /* +48 */
    char     artist[OXEVY_MUSIC_MAX_TEXT];   /* +56 */
    char     title[OXEVY_MUSIC_MAX_TEXT];    /* +568 */
    char     status[OXEVY_MUSIC_MAX_STATUS]; /* +1080 */
    char     player_id[OXEVY_MUSIC_MAX_PLAYER]; /* +1144 */
    char     art_url[OXEVY_MUSIC_MAX_ARTURL];   /* +1208 */
};

#define OXEVY_MUSIC_HEADER_SIZE  (56 + OXEVY_MUSIC_MAX_TEXT*2 + OXEVY_MUSIC_MAX_STATUS + OXEVY_MUSIC_MAX_PLAYER + OXEVY_MUSIC_MAX_ARTURL)
/* == 56 + 1024 + 64 + 64 + 1024 = 2232 */

#ifdef __cplusplus
}
#endif

#endif /* OXEVY_MUSIC_PROTOCOL_H */

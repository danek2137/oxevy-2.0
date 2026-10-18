/*
 * oxevy native music bridge - Linux (D-Bus / MPRIS) implementation.
 *
 * Queries the org.mpris.MediaPlayer2.Player interface of the currently
 * playing player via D-Bus and writes now-playing metadata (title, artist,
 * playback status, position, length) plus the album-art URL (mpris:artUrl)
 * into the /oxevy-music POSIX shared-memory segment that the Java mod reads.
 *
 * Built to liboxevy-music.so and loaded by the JVM via JNI (NativeMusicLoader).
 */

#include <jni.h>

#include "oxevy_music_protocol.h"

#include <algorithm>
#include <atomic>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <dbus/dbus.h>

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {

std::mutex g_mutex;
std::atomic<bool> g_running{false};
std::thread g_thread;
int g_shm_fd = -1;
OxevyMusicHeader* g_shm = nullptr;  // mapped view
long long g_started_ms = 0;

// ── helpers ──────────────────────────────────────────────────────────

long long nowMs() {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return static_cast<long long>(ts.tv_sec) * 1000LL + ts.tv_nsec / 1000000LL;
}

std::string jstr(JNIEnv* env, jstring s) {
    if (s == nullptr) return "";
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

void writeString(char* dst, size_t cap, const std::string& src) {
    if (cap == 0) return;
    size_t n = std::min(cap - 1, src.size());
    std::memcpy(dst, src.data(), n);
    dst[n] = '\0';
}

// ── shared memory ────────────────────────────────────────────────────

bool mapSharedMemory() {
    if (g_shm) return true;
    int fd = shm_open(OXEVY_MUSIC_SHM_NAME, O_CREAT | O_RDWR, 0600);
    if (fd < 0) return false;
    if (ftruncate(fd, OXEVY_MUSIC_HEADER_SIZE) != 0) {
        ::close(fd);
        return false;
    }
    void* addr = mmap(nullptr, OXEVY_MUSIC_HEADER_SIZE, PROT_READ | PROT_WRITE,
                      MAP_SHARED, fd, 0);
    ::close(fd);
    if (addr == MAP_FAILED) return false;
    g_shm_fd = fd;  // keep original fd? we closed it; maps survive close
    g_shm = static_cast<OxevyMusicHeader*>(addr);
    std::memset(g_shm, 0, OXEVY_MUSIC_HEADER_SIZE);
    g_shm->magic = OXEVY_MUSIC_MAGIC;
    g_shm->version = OXEVY_MUSIC_VERSION;
    g_shm->struct_size = OXEVY_MUSIC_HEADER_SIZE;
    return true;
}

void commitShm() {
    if (!g_shm) return;
    g_shm->updated_ms = static_cast<uint64_t>(nowMs());
    g_shm->sequence++;  // non-atomic bump; reader treats seq as advisory
}

void clearShm() {
    if (!g_shm) return;
    const uint32_t magic = g_shm->magic;
    const uint32_t version = g_shm->version;
    const uint32_t ssize = g_shm->struct_size;
    std::memset(g_shm, 0, OXEVY_MUSIC_HEADER_SIZE);
    g_shm->magic = magic;
    g_shm->version = version;
    g_shm->struct_size = ssize;
}

// ── D-Bus / MPRIS ────────────────────────────────────────────────────

struct TrackInfo {
    bool has = false;
    bool playing = false;
    int64_t pos_us = 0;
    int64_t len_us = 0;
    std::string artist, title, status, player, art_url;
};

/*
 * variant string: reads a D-Bus value returned as a VARIANT (e.g. a
 * DBus.Properties.Get reply) into a std::string. The iterator must be
 * positioned at the VARIANT; it recurses into it. Returns true on success.
 */
bool variantAsString(DBusMessageIter* iter, std::string& out) {
    DBusMessageIter v;
    if (dbus_message_iter_get_arg_type(iter) != DBUS_TYPE_VARIANT) return false;
    dbus_message_iter_recurse(iter, &v);
    if (dbus_message_iter_get_arg_type(&v) != DBUS_TYPE_STRING) return false;
    const char* s = nullptr;
    dbus_message_iter_get_basic(&v, &s);
    if (s) out = s;
    return true;
}

/*
 * Get a property off a player over an open connection. On success, outVar is
 * left pointing at the VARIANT reply element (NOT recursed), matching
 * variantAsString()/recurseVariant() which consume a VARIANT.
 */
bool getProperty(DBusConnection* conn, const std::string& dest,
                 const char* interface, const char* name, DBusMessageIter& outVar) {
    DBusMessage* call = dbus_message_new_method_call(
        dest.c_str(), "/org/mpris/MediaPlayer2",
        "org.freedesktop.DBus.Properties", "Get");
    if (!call) return false;
    dbus_message_append_args(call, DBUS_TYPE_STRING, &interface,
                             DBUS_TYPE_STRING, &name, DBUS_TYPE_INVALID);
    DBusError err;
    dbus_error_init(&err);
    DBusMessage* reply = dbus_connection_send_with_reply_and_block(conn, call, 2000, &err);
    dbus_message_unref(call);
    if (!reply) {
        dbus_error_free(&err);
        return false;
    }
    bool ok = false;
    DBusMessageIter root;
    if (dbus_message_iter_init(reply, &root) &&
        dbus_message_iter_get_arg_type(&root) == DBUS_TYPE_VARIANT) {
        outVar = root;  // positioned at the VARIANT; caller recurses
        ok = true;
    }
    dbus_message_unref(reply);
    dbus_error_free(&err);
    return ok;
}

/* Recurse into a VARIANT so callers can inspect the concrete value. */
bool recurseVariant(DBusMessageIter* variantIter, DBusMessageIter& out) {
    if (dbus_message_iter_get_arg_type(variantIter) != DBUS_TYPE_VARIANT) return false;
    dbus_message_iter_recurse(variantIter, &out);
    return true;
}

void fillTrack(DBusConnection* conn, const std::string& player, TrackInfo& t) {
    t.player = player;

    // PlaybackStatus
    {
        DBusMessageIter v;
        if (getProperty(conn, player, "org.mpris.MediaPlayer2.Player",
                        "PlaybackStatus", v)) {
            std::string s;
            if (variantAsString(&v, s)) {
                t.status = s;
                t.has = true;
                t.playing = (s == "Playing");
            }
        }
    }

    // Metadata dict (a{sv}): title, artist (string or array-of-string), artUrl, length
    {
        DBusMessageIter v;
        if (getProperty(conn, player, "org.mpris.MediaPlayer2.Player",
                        "Metadata", v)) {
            DBusMessageIter arr;
            if (recurseVariant(&v, arr) &&
                dbus_message_iter_get_arg_type(&arr) == DBUS_TYPE_ARRAY) {
                DBusMessageIter dict;
                dbus_message_iter_recurse(&arr, &dict);
                while (dbus_message_iter_get_arg_type(&dict) == DBUS_TYPE_DICT_ENTRY) {
                    DBusMessageIter entry;
                    dbus_message_iter_recurse(&dict, &entry);
                    const char* key = nullptr;
                    if (dbus_message_iter_get_arg_type(&entry) == DBUS_TYPE_STRING) {
                        dbus_message_iter_get_basic(&entry, &key);
                        dbus_message_iter_next(&entry);  // -> value (VARIANT)

                        if (key && std::strcmp(key, "xesam:title") == 0) {
                            std::string val;
                            if (variantAsString(&entry, val)) t.title = val;
                        } else if (key && std::strcmp(key, "xesam:artist") == 0) {
                            std::string val;
                            if (variantAsString(&entry, val)) {
                                if (!t.artist.empty()) t.artist += ", ";
                                t.artist += val;
                            } else {
                                // artist may be an array of strings (as)
                                DBusMessageIter av;
                                if (recurseVariant(&entry, av) &&
                                    dbus_message_iter_get_arg_type(&av) == DBUS_TYPE_ARRAY) {
                                    DBusMessageIter asIt;
                                    dbus_message_iter_recurse(&av, &asIt);
                                    while (dbus_message_iter_get_arg_type(&asIt) == DBUS_TYPE_STRING) {
                                        const char* s = nullptr;
                                        dbus_message_iter_get_basic(&asIt, &s);
                                        if (s) {
                                            if (!t.artist.empty()) t.artist += ", ";
                                            t.artist += s;
                                        }
                                        dbus_message_iter_next(&asIt);
                                    }
                                }
                            }
                        } else if (key && std::strcmp(key, "mpris:artUrl") == 0) {
                            std::string val;
                            if (variantAsString(&entry, val)) t.art_url = val;
                        } else if (key && std::strcmp(key, "mpris:length") == 0) {
                            DBusMessageIter vv;
                            if (recurseVariant(&entry, vv)) {
                                if (dbus_message_iter_get_arg_type(&vv) == DBUS_TYPE_INT64) {
                                    int64_t v64 = 0;
                                    dbus_message_iter_get_basic(&vv, &v64);
                                    t.len_us = v64;
                                } else if (dbus_message_iter_get_arg_type(&vv) == DBUS_TYPE_UINT64) {
                                    uint64_t u64 = 0;
                                    dbus_message_iter_get_basic(&vv, &u64);
                                    t.len_us = static_cast<int64_t>(u64);
                                }
                            }
                        }
                    }
                    dbus_message_iter_next(&dict);
                }
                if (!t.title.empty() || !t.artist.empty()) t.has = true;
            }
        }
    }

    // Position (int64 microseconds)
    {
        DBusMessageIter v;
        if (getProperty(conn, player, "org.mpris.MediaPlayer2.Player",
                        "Position", v)) {
            DBusMessageIter posIter;
            if (recurseVariant(&v, posIter) &&
                dbus_message_iter_get_arg_type(&posIter) == DBUS_TYPE_INT64) {
                int64_t pos = 0;
                dbus_message_iter_get_basic(&posIter, &pos);
                t.pos_us = pos;
            }
        }
    }
}

/* List currently registered MPRIS players. */
std::vector<std::string> listPlayers(DBusConnection* conn) {
    std::vector<std::string> out;
    DBusMessage* call = dbus_message_new_method_call(
        "org.freedesktop.DBus", "/org/freedesktop/DBus",
        "org.freedesktop.DBus", "ListNames");
    if (!call) return out;
    DBusError err;
    dbus_error_init(&err);
    DBusMessage* reply = dbus_connection_send_with_reply_and_block(conn, call, 2000, &err);
    dbus_message_unref(call);
    if (!reply) { dbus_error_free(&err); return out; }
    DBusMessageIter root, arr;
    if (dbus_message_iter_init(reply, &root) &&
        dbus_message_iter_get_arg_type(&root) == DBUS_TYPE_ARRAY) {
        dbus_message_iter_recurse(&root, &arr);
        while (dbus_message_iter_get_arg_type(&arr) == DBUS_TYPE_STRING) {
            const char* name = nullptr;
            dbus_message_iter_get_basic(&arr, &name);
            if (name && std::string(name).find("org.mpris.MediaPlayer2.") == 0) {
                out.push_back(name);
            }
            dbus_message_iter_next(&arr);
        }
    }
    dbus_message_unref(reply);
    dbus_error_free(&err);
    return out;
}

TrackInfo queryBest(DBusConnection* conn) {
    std::vector<std::string> players = listPlayers(conn);
    TrackInfo best;
    int bestScore = -1;
    for (const auto& p : players) {
        TrackInfo t;
        fillTrack(conn, p, t);
        if (!t.has) continue;
        int score = t.playing ? 2 : 1;
        if (!t.title.empty()) score += 4;
        if (!t.artist.empty()) score += 2;
        if (!t.art_url.empty()) score += 1;
        if (score > bestScore) {
            bestScore = score;
            best = t;
        }
    }
    return best;
}

void writeTrack(const TrackInfo& t) {
    if (!g_shm) return;
    clearShm();
    if (!t.has) { commitShm(); return; }
    g_shm->has_track = 1;
    g_shm->playing = t.playing ? 1 : 0;
    g_shm->position_us = t.pos_us;
    g_shm->length_us = t.len_us;
    writeString(g_shm->artist, OXEVY_MUSIC_MAX_TEXT, t.artist);
    writeString(g_shm->title, OXEVY_MUSIC_MAX_TEXT, t.title);
    writeString(g_shm->status, OXEVY_MUSIC_MAX_STATUS, t.status);
    writeString(g_shm->player_id, OXEVY_MUSIC_MAX_PLAYER, t.player);
    writeString(g_shm->art_url, OXEVY_MUSIC_MAX_ARTURL, t.art_url);
    commitShm();
}

bool pollOnce() {
    DBusError err;
    dbus_error_init(&err);
    // dbus_bus_get() returns a process-shared connection: it must NOT be
    // closed or unref'd (calling dbus_connection_close() on it can deadlock),
    // so we reuse it for the life of the process.
    DBusConnection* conn = dbus_bus_get(DBUS_BUS_SESSION, &err);
    if (!conn) {
        dbus_error_free(&err);
        writeTrack(TrackInfo{});
        return false;
    }
    TrackInfo t = queryBest(conn);
    writeTrack(t);
    dbus_error_free(&err);
    return t.has;
}

void loop() {
    while (g_running) {
        pollOnce();
        for (int i = 0; i < 40 && g_running; i++) {
            struct timespec ts{0, 100 * 1000 * 1000};  // 100ms
            nanosleep(&ts, nullptr);
        }
    }
}

void startThread() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_running) return;
    if (!mapSharedMemory()) return;
    g_started_ms = nowMs();
    g_running = true;
    g_thread = std::thread(loop);
}

void stopThread() {
    g_running = false;
    if (g_thread.joinable()) g_thread.join();
    if (g_shm) {
        clearShm();
        munmap(g_shm, OXEVY_MUSIC_HEADER_SIZE);
        g_shm = nullptr;
    }
    if (g_shm_fd >= 0) { ::close(g_shm_fd); g_shm_fd = -1; }
    shm_unlink(OXEVY_MUSIC_SHM_NAME);
}

}  // namespace

// ── JNI entry points ──────────────────────────────────────────────────

extern "C" {

/* NativeMusicLoader.start() */
JNIEXPORT jboolean JNICALL
Java_com_oxevy_safemod_music_NativeMusicLoader_start(JNIEnv*, jclass) {
    startThread();
    return g_running ? JNI_TRUE : JNI_FALSE;
}

/* NativeMusicLoader.isRunning() */
JNIEXPORT jboolean JNICALL
Java_com_oxevy_safemod_music_NativeMusicLoader_isRunning(JNIEnv*, jclass) {
    return g_running ? JNI_TRUE : JNI_FALSE;
}

/* NativeMusicLoader.stop() */
JNIEXPORT void JNICALL
Java_com_oxevy_safemod_music_NativeMusicLoader_stop(JNIEnv*, jclass) {
    stopThread();
}

/* NativeMusicLoader.pollNow() - force a synchronous refresh, returns has_track */
JNIEXPORT jboolean JNICALL
Java_com_oxevy_safemod_music_NativeMusicLoader_pollNow(JNIEnv*, jclass) {
    bool has = pollOnce();
    return has ? JNI_TRUE : JNI_FALSE;
}

/* NativeMusicLoader.getShmFilePath() - POSIX shm is backed by a real file. */
JNIEXPORT jstring JNICALL
Java_com_oxevy_safemod_music_NativeMusicLoader_getShmFilePath(JNIEnv* env, jclass) {
    std::string path = "/dev/shm/" OXEVY_MUSIC_SHM_NAME;
    return env->NewStringUTF(path.c_str());
}

}  // extern "C"

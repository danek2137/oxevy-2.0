/*
 * oxevy native Discord RPC bridge - Linux (POSIX) implementation.
 *
 * Connects to Discord over a unix socket (discord-ipc-N) and speaks the
 * Discord IPC protocol directly. Built to liboxevy-rpc.so and loaded by the
 * JVM via JNI at launch.
 *
 * Socket discovery covers:
 *   $XDG_RUNTIME_DIR/discord-ipc-N          (native Discord)
 *   /tmp/discord-ipc-N                      (fallback)
 *   $XDG_RUNTIME_DIR/app/com.discordapp.Discord/discord-ipc-N   (Flatpak)
 *   $XDG_RUNTIME_DIR/.flatpak/com.discordapp.Discord/xdg-run/... (Flatpak alt)
 */

#include <jni.h>

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <mutex>
#include <random>
#include <string>
#include <thread>
#include <vector>

#include <poll.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

namespace {

// ── globals ──────────────────────────────────────────────────────────
std::mutex g_mutex;
std::atomic<bool> g_running{false};
std::thread g_reader;
int g_fd = -1;

// ── helpers ──────────────────────────────────────────────────────────

std::string jstr(JNIEnv* env, jstring s) {
    if (s == nullptr) return "";
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

std::string jsonEscape(const std::string& in) {
    std::string out;
    out.reserve(in.size() + 16);
    for (unsigned char c : in) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out;
}

std::string randomNonce() {
    static std::random_device rd;
    static std::mt19937 gen(rd());
    static std::uniform_int_distribution<int> dist(0, 15);
    static const char* hex = "0123456789abcdef";
    std::string s;
    s.reserve(16);
    for (int i = 0; i < 16; i++) s += hex[dist(gen)];
    return s;
}

long long processId() {
    return static_cast<long long>(getpid());
}

long long nowMs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<long long>(ts.tv_sec) * 1000LL + ts.tv_nsec / 1000000LL;
}

// ── frame I/O ─────────────────────────────────────────────────────────

void sendFrameNoLock(const std::string& payload, uint32_t opcode) {
    if (g_fd < 0) return;
    uint32_t len = static_cast<uint32_t>(payload.size());
    uint8_t header[8];
    header[0] = static_cast<uint8_t>(opcode & 0xFF);
    header[1] = static_cast<uint8_t>((opcode >> 8) & 0xFF);
    header[2] = static_cast<uint8_t>((opcode >> 16) & 0xFF);
    header[3] = static_cast<uint8_t>((opcode >> 24) & 0xFF);
    header[4] = static_cast<uint8_t>(len & 0xFF);
    header[5] = static_cast<uint8_t>((len >> 8) & 0xFF);
    header[6] = static_cast<uint8_t>((len >> 16) & 0xFF);
    header[7] = static_cast<uint8_t>((len >> 24) & 0xFF);
    ::write(g_fd, header, 8);
    if (len > 0) ::write(g_fd, payload.data(), payload.size());
}

void sendFrame(const std::string& payload, uint32_t opcode) {
    std::lock_guard<std::mutex> lock(g_mutex);
    sendFrameNoLock(payload, opcode);
}

bool socketReadable(int timeoutMs) {
    if (g_fd < 0) return false;
    pollfd pfd{g_fd, POLLIN, 0};
    int pr = poll(&pfd, 1, timeoutMs);
    if (pr > 0 && (pfd.revents & (POLLIN | POLLERR | POLLHUP))) return true;
    return false;
}

bool readFrame(uint32_t& opcode, std::string& payload) {
    uint8_t header[8];
    ssize_t got = ::recv(g_fd, header, 8, MSG_WAITALL);
    if (got != 8) return false;
    opcode = static_cast<uint32_t>(header[0]) |
             (static_cast<uint32_t>(header[1]) << 8) |
             (static_cast<uint32_t>(header[2]) << 16) |
             (static_cast<uint32_t>(header[3]) << 24);
    uint32_t len = static_cast<uint32_t>(header[4]) |
                   (static_cast<uint32_t>(header[5]) << 8) |
                   (static_cast<uint32_t>(header[6]) << 16) |
                   (static_cast<uint32_t>(header[7]) << 24);
    payload.clear();
    if (len > 0) {
        payload.resize(len);
        size_t readTotal = 0;
        while (readTotal < len) {
            ssize_t n = ::recv(g_fd, &payload[readTotal], len - readTotal, 0);
            if (n <= 0) return false;
            readTotal += static_cast<size_t>(n);
        }
    }
    return true;
}

// ── connection ────────────────────────────────────────────────────────

bool tryConnect(const std::string& base) {
    for (int i = 0; i < 10; i++) {
        std::string path = base + "/discord-ipc-" + std::to_string(i);
        int fd = socket(AF_UNIX, SOCK_STREAM, 0);
        if (fd < 0) return false;
        sockaddr_un addr{};
        addr.sun_family = AF_UNIX;
        if (path.size() >= sizeof(addr.sun_path)) {
            ::close(fd);
            continue;
        }
        std::strncpy(addr.sun_path, path.c_str(), sizeof(addr.sun_path) - 1);
        if (connect(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) == 0) {
            g_fd = fd;
            return true;
        }
        ::close(fd);
    }
    return false;
}

bool connectToDiscord() {
    std::vector<std::string> bases;
    if (const char* runtime = getenv("XDG_RUNTIME_DIR"); runtime && *runtime) {
        bases.push_back(runtime);  // native: /run/user/<uid>
        // Flatpak app sandbox runtime dir
        bases.push_back(std::string(runtime) + "/app/com.discordapp.Discord");
        // Flatpak alternate xdg-run mapping
        bases.push_back(std::string(runtime) +
                        "/.flatpak/com.discordapp.Discord/xdg-run");
    }
    bases.push_back("/tmp");
    for (const auto& base : bases) {
        if (tryConnect(base)) return true;
    }
    return false;
}

// ── public API ────────────────────────────────────────────────────────

void closeFd();
void readerLoop();

bool initialize(const std::string& clientId) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_running) return true;
    if (!connectToDiscord()) return false;

    std::string handshake = "{\"v\":1,\"client_id\":\"" + jsonEscape(clientId) + "\"}";
    sendFrameNoLock(handshake, 0);

    long long deadline = nowMs() + 3000;
    while (nowMs() < deadline) {
        if (socketReadable(100)) {
            uint32_t op;
            std::string payload;
            if (!readFrame(op, payload)) {
                closeFd();
                return false;
            }
            if (op == 1 && payload.find("READY") != std::string::npos) {
                g_running = true;
                g_reader = std::thread(readerLoop);
                return true;
            }
            if (op == 2) {  // CLOSE
                closeFd();
                return false;
            }
        }
    }
    closeFd();
    return false;
}

void updatePresence(const std::string& details, const std::string& state,
                    const std::string& largeImage, const std::string& smallImage,
                    long long startTs, long long endTs) {
    if (!g_running) return;

    std::string activity;
    activity += "{\"type\":0";
    if (!state.empty()) activity += ",\"state\":\"" + jsonEscape(state) + "\"";
    if (!details.empty()) activity += ",\"details\":\"" + jsonEscape(details) + "\"";
    if (startTs > 0 || endTs > 0) {
        activity += ",\"timestamps\":{";
        bool first = true;
        if (startTs > 0) {
            activity += "\"start\":" + std::to_string(startTs);
            first = false;
        }
        if (endTs > 0) {
            if (!first) activity += ",";
            activity += "\"end\":" + std::to_string(endTs);
        }
        activity += "}";
    }
    if (!largeImage.empty() || !smallImage.empty()) {
        activity += ",\"assets\":{";
        bool first = true;
        if (!largeImage.empty()) {
            activity += "\"large_image\":\"" + jsonEscape(largeImage) + "\"";
            first = false;
        }
        if (!smallImage.empty()) {
            if (!first) activity += ",";
            activity += "\"small_image\":\"" + jsonEscape(smallImage) + "\"";
        }
        activity += "}";
    }
    activity += "}";

    std::string payload =
        "{\"cmd\":\"SET_ACTIVITY\",\"nonce\":\"" + randomNonce() +
        "\",\"args\":{\"pid\":" + std::to_string(processId()) +
        ",\"activity\":" + activity + "}}";
    sendFrame(payload, 1);
}

void closeFd() {
    if (g_fd >= 0) {
        ::close(g_fd);
        g_fd = -1;
    }
}

void readerLoop() {
    while (g_running) {
        if (!socketReadable(100)) continue;
        if (g_fd < 0) break;
        uint32_t op;
        std::string payload;
        if (!readFrame(op, payload)) {
            g_running = false;
            break;
        }
        if (op == 2) {  // CLOSE
            g_running = false;
            break;
        }
        if (op == 3) {  // PING -> PONG
            sendFrame("", 4);
        }
    }
}

void shutdown() {
    g_running = false;
    closeFd();
    if (g_reader.joinable()) g_reader.join();
}

}  // namespace

// ── JNI entry points ──────────────────────────────────────────────────

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_oxevy_safemod_discord_NativeDiscordRpc_initialize(JNIEnv* env, jclass,
                                                           jstring clientId) {
    std::string id = jstr(env, clientId);
    return initialize(id) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_oxevy_safemod_discord_NativeDiscordRpc_updatePresence(
    JNIEnv* env, jclass, jstring details, jstring state, jstring largeImage,
    jstring smallImage, jlong startTimestamp, jlong endTimestamp) {
    updatePresence(jstr(env, details), jstr(env, state), jstr(env, largeImage),
                   jstr(env, smallImage), static_cast<long long>(startTimestamp),
                   static_cast<long long>(endTimestamp));
}

JNIEXPORT jboolean JNICALL
Java_com_oxevy_safemod_discord_NativeDiscordRpc_isRunning(JNIEnv*, jclass) {
    return g_running ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_oxevy_safemod_discord_NativeDiscordRpc_shutdown(JNIEnv*, jclass) {
    shutdown();
}

}  // extern "C"

/*
 * oxevy native Discord RPC bridge - Windows implementation.
 *
 * Connects to Discord over a named pipe (\\.\pipe\discord-ipc-N) and speaks
 * the Discord IPC protocol directly. Built to oxevy-rpc.dll and loaded by the
 * JVM via JNI at launch.
 */

#include <jni.h>

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <random>
#include <string>
#include <thread>
#include <vector>

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

namespace {

// ── globals ──────────────────────────────────────────────────────────
std::mutex g_mutex;
std::atomic<bool> g_running{false};
std::thread g_reader;
HANDLE g_fd = INVALID_HANDLE_VALUE;

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
    return static_cast<long long>(GetCurrentProcessId());
}

long long nowMs() {
    return static_cast<long long>(GetTickCount64());
}

// ── frame I/O ─────────────────────────────────────────────────────────

void sendFrameNoLock(const std::string& payload, uint32_t opcode) {
    if (g_fd == INVALID_HANDLE_VALUE) return;
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
    DWORD written;
    WriteFile(g_fd, header, 8, &written, nullptr);
    if (len > 0) WriteFile(g_fd, payload.data(), len, &written, nullptr);
}

void sendFrame(const std::string& payload, uint32_t opcode) {
    std::lock_guard<std::mutex> lock(g_mutex);
    sendFrameNoLock(payload, opcode);
}

bool socketReadable(int timeoutMs) {
    (void)timeoutMs;
    if (g_fd == INVALID_HANDLE_VALUE) return false;
    DWORD avail = 0;
    if (PeekNamedPipe(g_fd, nullptr, 0, nullptr, &avail, nullptr)) {
        return avail >= 8;
    }
    return false;
}

bool readFrame(uint32_t& opcode, std::string& payload) {
    uint8_t header[8];
    DWORD got = 0;
    if (!ReadFile(g_fd, header, 8, &got, nullptr) || got != 8) return false;
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
            DWORD r = 0;
            if (!ReadFile(g_fd, &payload[readTotal], len - readTotal, &r, nullptr))
                return false;
            readTotal += r;
        }
    }
    return true;
}

// ── connection ────────────────────────────────────────────────────────

bool connectToDiscord() {
    for (int i = 0; i < 10; i++) {
        std::string pipe = "\\\\.\\pipe\\discord-ipc-" + std::to_string(i);
        HANDLE h = CreateFileA(pipe.c_str(), GENERIC_READ | GENERIC_WRITE, 0,
                               nullptr, OPEN_EXISTING, 0, nullptr);
        if (h != INVALID_HANDLE_VALUE) {
            g_fd = h;
            return true;
        }
        if (GetLastError() == ERROR_PIPE_BUSY) {
            Sleep(100);
            continue;
        }
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
    if (g_fd != INVALID_HANDLE_VALUE) {
        CloseHandle(g_fd);
        g_fd = INVALID_HANDLE_VALUE;
    }
}

void readerLoop() {
    while (g_running) {
        if (!socketReadable(100)) {
            Sleep(50);
            continue;
        }
        if (g_fd == INVALID_HANDLE_VALUE) break;
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

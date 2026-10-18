/*
 * oxevy native music bridge - Windows implementation.
 *
 * Reads the system media session (GlobalSystemMediaTransportControlsSession)
 * via a small PowerShell bridge and writes now-playing metadata plus the
 * album-art thumbnail file path into the named shared-memory segment
 * "oxevy_music" that the Java mod reads.
 *
 * The PowerShell one-liner is used because the Windows Runtime COM APIs for
 * media transport controls are impractical to consume directly from a plain
 * MinGW C++ build. It is the same mechanism the previous pure-Java module
 * used, now executed from C++ and published through shared memory.
 *
 * Built to oxevy_music.dll and loaded by the JVM via JNI (NativeMusicLoader).
 */

#include <jni.h>

#include "oxevy_music_protocol.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <vector>

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <wchar.h>

namespace {

std::mutex g_mutex;
std::atomic<bool> g_running{false};
std::thread g_thread;
HANDLE g_map = nullptr;
HANDLE g_file = nullptr;
OxevyMusicHeader* g_shm = nullptr;
std::wstring g_shm_path;

void writeString(char* dst, size_t cap, const std::string& src) {
    if (cap == 0) return;
    size_t n = src.size() < (cap - 1) ? src.size() : (cap - 1);
    std::memcpy(dst, src.data(), n);
    dst[n] = '\0';
}

long long nowMs() {
    FILETIME ft;
    GetSystemTimeAsFileTime(&ft);
    ULARGE_INTEGER li;
    li.LowPart = ft.dwLowDateTime;
    li.HighPart = ft.dwHighDateTime;
    // 100ns intervals since 1601; rebase to unix epoch and scale to ms.
    const long long UNIX_EPOCH_100NS = 116444736000000000LL;
    return (li.QuadPart - UNIX_EPOCH_100NS) / 10000LL;
}

std::string narrowUtf8(const std::wstring& w) {
    if (w.empty()) return "";
    int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), -1, nullptr, 0, nullptr, nullptr);
    if (n <= 0) return "";
    std::string out(n, '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), -1, &out[0], n, nullptr, nullptr);
    out.resize(n - 1);
    return out;
}

bool mapSharedMemory() {
    if (g_shm) return true;
    // Back the shared region with a real temp file so the Java side can open
    // the same path with FileChannel.map (POSIX /dev/shm equivalent).
    wchar_t tmpDir[MAX_PATH];
    if (!GetTempPathW(MAX_PATH, tmpDir)) return false;
    g_shm_path = std::wstring(tmpDir) + L"oxevy-music.shm";
    g_file = CreateFileW(g_shm_path.c_str(), GENERIC_READ | GENERIC_WRITE,
                          FILE_SHARE_READ | FILE_SHARE_WRITE, nullptr,
                          CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (g_file == INVALID_HANDLE_VALUE) { g_file = nullptr; return false; }
    DWORD high = 0;
    DWORD low = (DWORD)OXEVY_MUSIC_HEADER_SIZE;
    g_map = CreateFileMappingW(g_file, nullptr, PAGE_READWRITE, high, low, nullptr);
    if (!g_map) { CloseHandle(g_file); g_file = nullptr; return false; }
    g_shm = static_cast<OxevyMusicHeader*>(
        MapViewOfFile(g_map, FILE_MAP_ALL_ACCESS, 0, 0, OXEVY_MUSIC_HEADER_SIZE));
    if (!g_shm) {
        CloseHandle(g_map); g_map = nullptr;
        CloseHandle(g_file); g_file = nullptr;
        return false;
    }
    std::memset(g_shm, 0, OXEVY_MUSIC_HEADER_SIZE);
    g_shm->magic = OXEVY_MUSIC_MAGIC;
    g_shm->version = OXEVY_MUSIC_VERSION;
    g_shm->struct_size = OXEVY_MUSIC_HEADER_SIZE;
    return true;
}

void commitShm() {
    if (!g_shm) return;
    g_shm->updated_ms = static_cast<uint64_t>(nowMs());
    g_shm->sequence++;
}

void clearShm() {
    if (!g_shm) return;
    const uint32_t magic = g_shm->magic, version = g_shm->version, ssize = g_shm->struct_size;
    std::memset(g_shm, 0, OXEVY_MUSIC_HEADER_SIZE);
    g_shm->magic = magic;
    g_shm->version = version;
    g_shm->struct_size = ssize;
}

// ── PowerShell media bridge ───────────────────────────────────────────

std::string runPowershell(const std::wstring& script) {
    // Build "-EncodedCommand" with a UTF-16LE base64 script to avoid quoting hell.
    std::wstring cmd = L"powershell -NoProfile -NonInteractive -EncodedCommand ";
    // base64 of UTF-16LE
    static const char b64[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string bin;
    for (wchar_t c : script) {
        bin.push_back(static_cast<char>(c & 0xFF));
        bin.push_back(static_cast<char>((c >> 8) & 0xFF));
    }
    std::string enc;
    int i = 0;
    for (size_t n = bin.size(); i + 2 < (int)n; i += 3) {
        enc += b64[(bin[i] >> 2) & 0x3F];
        enc += b64[((bin[i] & 0x03) << 4) | ((bin[i + 1] >> 4) & 0x0F)];
        enc += b64[((bin[i + 1] & 0x0F) << 2) | ((bin[i + 2] >> 6) & 0x03)];
        enc += b64[bin[i + 2] & 0x3F];
    }
    if (i + 1 == (int)bin.size()) {
        enc += b64[(bin[i] >> 2) & 0x3F];
        enc += b64[((bin[i] & 0x03) << 4)];
        enc += '=';
        enc += '=';
    } else if (i + 2 == (int)bin.size()) {
        enc += b64[(bin[i] >> 2) & 0x3F];
        enc += b64[((bin[i] & 0x03) << 4) | ((bin[i + 1] >> 4) & 0x0F)];
        enc += b64[((bin[i + 1] & 0x0F) << 2)];
        enc += '=';
    }
    cmd += std::wstring(enc.begin(), enc.end());

    STARTUPINFOW si{sizeof(si)};
    PROCESS_INFORMATION pi{};
    SECURITY_ATTRIBUTES sa{sizeof(sa), nullptr, TRUE};
    HANDLE readPipe = nullptr, writePipe = nullptr;
    if (!CreatePipe(&readPipe, &writePipe, &sa, 0)) return "";
    SetHandleInformation(readPipe, HANDLE_FLAG_INHERIT, 0);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdOutput = writePipe;
    si.hStdError = writePipe;
    std::wstring cl = L"cmd.exe /c " + cmd;
    std::vector<wchar_t> buf(cl.begin(), cl.end());
    buf.push_back(L'\0');
    if (!CreateProcessW(nullptr, buf.data(), nullptr, nullptr, TRUE,
                        CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        CloseHandle(readPipe);
        CloseHandle(writePipe);
        return "";
    }
    CloseHandle(writePipe);
    std::string out;
    char chunk[4096];
    DWORD rd = 0;
    while (ReadFile(readPipe, chunk, sizeof(chunk) - 1, &rd, nullptr) && rd > 0) {
        chunk[rd] = 0;
        out += chunk;
    }
    CloseHandle(readPipe);
    WaitForSingleObject(pi.hProcess, 5000);
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return out;
}

struct TrackInfo {
    bool has = false;
    bool playing = false;
    long long pos_us = 0, len_us = 0;
    std::string artist, title, status, player, art_url;
};

/*
 * PowerShell: query the current session, gather artist/title/status/length
 * and save the thumbnail to a temp PNG, printing a '|'-separated line plus
 * the thumbnail path on its own line prefixed with "ART=".
 */
const wchar_t* MEDIA_SCRIPT =
    L"$ErrorActionPreference='SilentlyContinue';"
    L"$m=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime];"
    L"$g=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync().GetResults();"
    L"if(-not $g){exit};"
    L"$s=$g.GetCurrentSession();"
    L"if($s){"
    L"$p=$s.TryGetMediaPropertiesAsync().GetResults();"
    L"$pi=$s.GetPlaybackInfo();"
    L"$st=$pi.PlaybackStatus.ToString();"
    L"$len=0; try{$len=[int64]$p.PlaybackRate}catch{};"
    L"$pos=0; try{$pos=[int64]($pi.PlaybackPosition.TotalMilliseconds*1000)}catch{};"
    L"Write-Output ($p.Artist + '|' + $p.Title + '|' + $st + '|' + $pos);"
    L"try{"
    L"$t=$p.Thumbnail;"
    L"if($t){"
    L"$stream=$t.OpenReadAsync().GetResults();"
    L"$file=[System.IO.Path]::Combine([System.IO.Path]::GetTempPath(),'oxevy_music_thumb.png');"
    L"$fs=[System.IO.File]::Create($file);"
    L"$dr=New-Object System.IO.MemoryStream;"
    L"$fs.CopyTo($dr);$dr.Seek(0,'Begin');$fs.close();"
    L"if($dr.Length -gt 0){[System.IO.File]::WriteAllBytes($file,$dr.ToArray());Write-Output ('ART=' + $file);}"
    L"}}}catch{}";

bool pollOnce() {
    if (!g_shm) return false;
    std::string raw = runPowershell(MEDIA_SCRIPT);
    clearShm();
    if (raw.empty()) { commitShm(); return false; }

    TrackInfo t;
    std::string artFile;
    // Split lines: first line is the media line, later lines may hold ART=...,
    // our own write-to-console won't add stderr noise.
    std::string firstLine;
    size_t start = 0;
    bool first = true;
    for (;;) {
        size_t nl = raw.find('\n', start);
        std::string line = raw.substr(start, nl == std::string::npos ? std::string::npos : nl - start);
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (!line.empty()) {
            if (line.rfind("ART=", 0) == 0) artFile = line.substr(4);
            else if (first) { firstLine = line; first = false; }
        }
        if (nl == std::string::npos) break;
        start = nl + 1;
    }

    if (!firstLine.empty()) {
        // artist|title|status|pos
        std::string fields[4];
        int idx = 0;
        size_t p = 0;
        for (; idx < 4 && p < firstLine.size();) {
            size_t bar = firstLine.find('|', p);
            std::string f = firstLine.substr(p, bar == std::string::npos ? std::string::npos : bar - p);
            fields[idx++] = f;
            if (bar == std::string::npos) break;
            p = bar + 1;
        }
        t.artist = fields[0];
        t.title = fields[1];
        t.status = fields[2];
        t.playing = (fields[2] == "Playing");
        if (!fields[3].empty()) t.pos_us = std::atoll(fields[3].c_str());
        t.player = "WindowsMedia";
        if (artFile.empty())
            t.art_url = "";
        else
            t.art_url = "file:///" + artFile;
        t.has = !t.title.empty() || !t.artist.empty();
    }

    g_shm->has_track = t.has ? 1 : 0;
    g_shm->playing = t.playing ? 1 : 0;
    g_shm->position_us = t.pos_us;
    g_shm->length_us = t.len_us;
    writeString(g_shm->artist, OXEVY_MUSIC_MAX_TEXT, t.artist);
    writeString(g_shm->title, OXEVY_MUSIC_MAX_TEXT, t.title);
    writeString(g_shm->status, OXEVY_MUSIC_MAX_STATUS, t.status);
    writeString(g_shm->player_id, OXEVY_MUSIC_MAX_PLAYER, t.player);
    writeString(g_shm->art_url, OXEVY_MUSIC_MAX_ARTURL, t.art_url);
    commitShm();
    return t.has;
}

void loop() {
    while (g_running) {
        pollOnce();
        Sleep(4000);
    }
}

void startThread() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_running) return;
    if (!mapSharedMemory()) return;
    g_running = true;
    g_thread = std::thread(loop);
}

void stopThread() {
    g_running = false;
    if (g_thread.joinable()) g_thread.join();
    if (g_shm) {
        clearShm();
        UnmapViewOfFile(g_shm);
        g_shm = nullptr;
    }
    if (g_map) { CloseHandle(g_map); g_map = nullptr; }
    if (g_file) { CloseHandle(g_file); g_file = nullptr; }
}

}  // namespace

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

/* NativeMusicLoader.pollNow() */
JNIEXPORT jboolean JNICALL
Java_com_oxevy_safemod_music_NativeMusicLoader_pollNow(JNIEnv*, jclass) {
    return pollOnce() ? JNI_TRUE : JNI_FALSE;
}

/* NativeMusicLoader.getShmFilePath() - real temp file backing the mapping. */
JNIEXPORT jstring JNICALL
Java_com_oxevy_safemod_music_NativeMusicLoader_getShmFilePath(JNIEnv* env, jclass) {
    std::string utf = narrowUtf8(g_shm_path);
    return env->NewStringUTF(utf.c_str());
}

}  // extern "C"

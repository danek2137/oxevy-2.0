package me.alpha432.oxevy.web

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.oxevy.safemod.music.AlbumArtLoader
import me.alpha432.oxevy.Oxevy
import me.alpha432.oxevy.features.modules.Module
import me.alpha432.oxevy.features.modules.client.ClickGuiModule
import me.alpha432.oxevy.features.modules.hud.MusicPlayerHudModule
import me.alpha432.oxevy.features.settings.Bind
import me.alpha432.oxevy.features.settings.Setting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import me.alpha432.oxevy.manager.CapeManager
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * ClickGuiWebBridge  –  Fabric 1.21 edition
 *
 * Uses a raw ServerSocket instead of com.sun.net.httpserver (which is
 * unavailable / restricted on the JVM bundled with modern Minecraft launchers).
 *
 * Endpoints:
 *   GET  /              → index.html
 *   GET  /style.css     → style.css
 *   GET  /app.js        → app.js
 *   GET  /api/state     → full module state JSON
 *   POST /api/module/toggle  body: {"module":"KillAura"}
 *   POST /api/setting        body: {"module":"X","setting":"Y","value":...}
 *   OPTIONS *           → CORS preflight
 */
object ClickGuiWebBridge {

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "WebGui-Worker").also { it.isDaemon = true }
    }

    // Album art for the main menu's "now playing" popup: PNG bytes keyed by the
    // MPRIS art URL, plus the URL warming was last kicked off for. Decoding a
    // cover costs a fetch, so the state endpoint starts it early; by the time the
    // page asks for the bytes they are normally already encoded here.
    private val musicArtCache = ConcurrentHashMap<String, ByteArray>()
    private val musicArtWarmed = AtomicReference<String>("")

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var activePort: Int = 8765

    // Cached /api/state payload, reused verbatim while nothing observable has
    // changed so idle polls neither rebuild the module tree nor re-serialize it.
    @Volatile private var cachedStateBody: ByteArray? = null
    @Volatile private var cachedStateRevision: Long = -1L

    // ── Public API ──────────────────────────────────────────────────────────

    @JvmStatic
    fun ensureStarted(): String {
        if (running.get()) return baseUrl()
        synchronized(this) {
            if (running.get()) return baseUrl()

            var bound: ServerSocket? = null
            for (port in 8765..8775) {
                try {
                    bound = ServerSocket(port).also { it.reuseAddress = true }
                    activePort = port
                    break
                } catch (_: Exception) {}
            }
            val ss = bound ?: error("Could not bind Web ClickGui on ports 8765-8775")
            serverSocket = ss
            running.set(true)

            // Bump a revision on every module toggle / setting update so the
            // /api/state poll can short-circuit to 304 when nothing changed.
            ClickGuiStateListener.ensureRegistered()

            Thread({
                Oxevy.LOGGER.info("[WebGui] Listening on {}", baseUrl())
                while (running.get()) {
                    val client = try { ss.accept() } catch (_: SocketException) { break } catch (_: Exception) { continue }
                    executor.submit { handleClient(client) }
                }
            }, "WebGui-Accept").also { it.isDaemon = true }.start()
        }
        return baseUrl()
    }

    @JvmStatic
    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    @JvmStatic
    fun launch(openBrowser: Boolean): String {
        val url = ensureStarted()
        if (openBrowser) {
            try {
                val desktop = Class.forName("java.awt.Desktop")
                val inst = desktop.getMethod("getDesktop").invoke(null)
                desktop.getMethod("browse", java.net.URI::class.java).invoke(inst, java.net.URI(url))
            } catch (_: Exception) {
                Oxevy.LOGGER.warn("[WebGui] Could not open browser automatically. Visit: {}", url)
            }
        }
        return url
    }

    // ── Raw HTTP server ─────────────────────────────────────────────────────

    private data class Request(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val bodyBytes: ByteArray,
        val body: String
    )

    private fun handleClient(socket: Socket) {
        try {
            // 30s so big multipart cape uploads on slow links can finish.
            socket.soTimeout = 30_000
            socket.use {
                val rawIn = it.getInputStream()
                val out   = it.getOutputStream()

                // Read the request head (request line + headers) byte-by-byte up
                // to the CRLFCRLF blank line. This must NOT go through a
                // BufferedReader: it would read ahead past the blank line and
                // swallow the POST body into its internal buffer, which makes
                // the following body read hang until the socket times out.
                val head = ByteArrayOutputStream(1024)
                val crlfCrlf = byteArrayOf(13, 10, 13, 10)
                var matched = 0
                while (matched < 4) {
                    val b = rawIn.read()
                    if (b == -1) return
                    head.write(b)
                    matched = if (b == crlfCrlf[matched].toInt()) matched + 1 else if (b == 13) 1 else 0
                }

                val headText = head.toString(StandardCharsets.ISO_8859_1)
                val headLines = headText.split("\r\n")
                val requestLine = headLines.firstOrNull() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val fullPath = parts[1]
                val path = fullPath.substringBefore("?")

                val headers = mutableMapOf<String, String>()
                for (i in 1 until headLines.size) {
                    val hl = headLines[i]
                    val colon = hl.indexOf(':')
                    if (colon != -1) {
                        headers[hl.substring(0, colon).trim().lowercase()] = hl.substring(colon + 1).trim()
                    }
                }

                // Read body as raw bytes for POST
                val bodyBytes: ByteArray = if (method == "POST") {
                    val len = headers["content-length"]?.toIntOrNull() ?: 0
                    val buf = ByteArray(len)
                    var read = 0
                    while (read < len) {
                        val n = rawIn.readNBytes(buf, read, len - read)
                        if (n == -1) break
                        read += n
                    }
                    buf
                } else ByteArray(0)

                val body = bodyBytes.toString(StandardCharsets.UTF_8)
                val req = Request(method, path, headers, bodyBytes, body)

                // Route
                when {
                    req.method == "OPTIONS"                                       -> sendCors(out)
                    req.method == "GET" && req.path == "/api/state"             -> handleState(out, req.headers["if-none-match"])
                    req.method == "GET" && req.path == "/api/island/state"      -> handleIslandState(out)
                    req.method == "POST" && req.path == "/api/module/toggle"    -> handleToggle(out, req.body)
                    req.method == "POST" && req.path == "/api/setting"          -> handleSetting(out, req.body)
                    req.method == "GET" && req.path == "/api/capes/state"       -> handleCapeState(out)
                    req.method == "POST" && req.path == "/api/cape/upload"      -> handleCapeUploadRaw(out, req)
                    req.method == "POST" && req.path == "/api/cape/select"      -> handleCapeSelect(out, req.body)
                    req.method == "GET" && req.path == "/api/capes/repo"        -> handleCapeRepo(out)
                    req.method == "GET" && req.path.startsWith("/capes/")       -> handleCapeFile(out, req.path)
                    req.method == "GET" && req.path.startsWith("/island/")      -> handleIslandStatic(out, req.path)
                    req.method == "POST" && req.path == "/api/mainmenu/navigate"  -> handleMainMenuNavigate(out, req.body)
                    req.method == "GET" && req.path == "/api/mainmenu/profile" -> handleProfile(out)
                    req.method == "GET" && req.path == "/api/music/state"      -> handleMusicState(out)
                    req.method == "GET" && req.path == "/api/music/art"        -> handleMusicArt(out)
                    req.method == "GET" && req.path == "/api/accounts/list"    -> handleAccountsList(out)
                    req.method == "POST" && req.path == "/api/accounts/add"     -> handleAccountsAdd(out, req.body)
                    req.method == "POST" && req.path == "/api/accounts/remove"  -> handleAccountsRemove(out, req.body)
                    req.method == "POST" && req.path == "/api/accounts/switch"  -> handleAccountsSwitch(out, req.body)
                    req.method == "GET" && req.path.startsWith("/accounts/")    -> handleAccountsStatic(out, req.path)
                    req.method == "GET" && req.path == "/api/cosmetics/list"    -> handleCosmeticsList(out)
                    req.method == "POST" && req.path == "/api/cosmetics/equip"   -> handleCosmeticsEquip(out, req.body)
                    req.method == "POST" && req.path == "/api/cosmetics/unequip" -> handleCosmeticsUnequip(out, req.body)
                    req.method == "POST" && req.path == "/api/cosmetics/theme"   -> handleCosmeticsTheme(out, req.body)
                    req.method == "GET" && req.path.startsWith("/cosmetics/")    -> handleCosmeticsStatic(out, req.path)
                    req.method == "GET" && req.path == "/config"                -> handleConfigStatic(out, "/config/")
                    req.method == "GET" && req.path.startsWith("/config/")       -> handleConfigStatic(out, req.path)
                    req.method == "GET"  && req.path == "/api/config/list"      -> handleConfigList(out)
                    req.method == "GET"  && req.path == "/api/config/current"   -> handleConfigCurrent(out)
                    req.method == "POST" && req.path == "/api/config/save"      -> handleConfigSave(out, req.body)
                    req.method == "POST" && req.path == "/api/config/load"      -> handleConfigLoad(out, req.body)
                    req.method == "POST" && req.path == "/api/config/delete"    -> handleConfigDelete(out, req.body)
                    req.method == "POST" && req.path == "/api/config/export"    -> handleConfigExport(out, req.body)
                    req.method == "POST" && req.path == "/api/config/import"    -> handleConfigImport(out, req.body)
                    req.method == "GET" && req.path.startsWith("/welcome/")     -> handleWelcomeStatic(out, req.path)
                    req.method == "POST" && req.path == "/api/welcome/done"     -> handleWelcomeDone(out)
                    req.method == "GET" && req.path.startsWith("/mainmenu/")    -> handleMainMenuStatic(out, req.path)
                    req.method == "GET" && req.path.startsWith("/shared/")     -> handleSharedStatic(out, req.path)
                    req.method == "GET" && req.path.startsWith("/pausemenu/")   -> handlePauseMenuStatic(out, req.path)
                    req.method == "GET"                                         -> handleStatic(out, req.path)
                    else -> sendText(out, 405, "Method Not Allowed")
                }
            }
        } catch (e: Exception) {
            Oxevy.LOGGER.debug("[WebGui] Client error: {}", e.message)
        }
    }

    // ── Route handlers ──────────────────────────────────────────────────────

    private fun handleState(out: OutputStream, ifNoneMatch: String?) {
        val revision = ClickGuiStateListener.revision()
        val cached = cachedStateBody
        if (cached != null && revision == cachedStateRevision) {
            if (ifNoneMatch != null && ifNoneMatch == etagFor(revision)) {
                sendStateNotModified(out, revision)
            } else {
                writeState(out, cached, revision)
            }
            return
        }
        // Cache miss: rebuild on the client thread, then remember the bytes so
        // an idle poll never has to touch the module tree again.
        val body = try {
            gson.toJson(onClientThread { buildState() }).toByteArray(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            sendText(out, 500, "State error: ${e.message}")
            return
        }
        // Label the snapshot with the revision read *before* building. A change
        // that lands meanwhile bumps the revision past it, forcing a rebuild on
        // the next poll rather than ever serving stale data under a fresh tag.
        cachedStateBody = body
        cachedStateRevision = revision
        writeState(out, body, revision)
    }

    /** Build and cache a fresh state snapshot (used after a toggle/setting write). */
    private fun respondState(out: OutputStream) {
        val revision = ClickGuiStateListener.revision()
        val body = try {
            gson.toJson(onClientThread { buildState() }).toByteArray(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            sendText(out, 500, "State error: ${e.message}")
            return
        }
        cachedStateBody = body
        cachedStateRevision = revision
        writeState(out, body, revision)
    }

    private fun etagFor(revision: Long) = "\"$revision\""

    private fun writeState(out: OutputStream, bytes: ByteArray, revision: Long) {
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("ETag: ${etagFor(revision)}\r\n")
            appendCors()
            append("Cache-Control: no-store\r\nConnection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun sendStateNotModified(out: OutputStream, revision: Long) {
        val header = buildString {
            append("HTTP/1.1 304 Not Modified\r\n")
            append("ETag: ${etagFor(revision)}\r\n")
            appendCors()
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    private fun handleIslandState(out: OutputStream) {
        val state = onClientThread { buildIslandState() }
        sendJson(out, 200, state)
    }

    private fun handleToggle(out: OutputStream, body: String) {
        val json = parseJson(body) ?: run { sendText(out, 400, "Bad JSON"); return }
        val moduleId = json.get("module")?.asString
        if (moduleId.isNullOrBlank()) { sendText(out, 400, "Missing module id"); return }

        val error = onClientThread {
            val m = Oxevy.moduleManager.getModuleByName(moduleId)
                ?: return@onClientThread "Unknown module: $moduleId"
            m.toggle()
            null
        }
        if (error != null) { sendText(out, 404, error); return }
        respondState(out)
    }

    private fun handleSetting(out: OutputStream, body: String) {
        val json = parseJson(body) ?: run { sendText(out, 400, "Bad JSON"); return }
        val moduleId    = json.get("module")?.asString
        val settingName = json.get("setting")?.asString
        val value       = json.get("value")
        if (moduleId.isNullOrBlank() || settingName.isNullOrBlank() || value == null) {
            sendText(out, 400, "Missing module, setting, or value"); return
        }
        val error = onClientThread { applySettingUpdate(moduleId, settingName, value) }
        if (error != null) { sendText(out, 400, error); return }
        respondState(out)
    }

    // ── Cape handlers ───────────────────────────────────────────────────────

    private fun handleCapeState(out: OutputStream) {
        val state = onClientThread { buildCapeState() }
        sendJson(out, 200, state)
    }

    private fun handleCapeUploadRaw(out: OutputStream, req: Request) {
        val contentType = req.headers["content-type"] ?: ""
        if (!contentType.contains("multipart/form-data")) {
            sendText(out, 400, "Only multipart/form-data supported for upload"); return
        }

        val boundary = contentType.substringAfter("boundary=", "").trim()
        if (boundary.isEmpty()) { sendText(out, 400, "No boundary found"); return }

        val parts = parseMultipartRaw(req.bodyBytes, boundary)
        val filePart = parts.firstOrNull { it.name == "file" }
            ?: run { sendText(out, 400, "No file part"); return }

        val result = onClientThread {
            val mgr = Oxevy.capeManager
            val path = mgr.saveUploadedCape(filePart.data, filePart.fileName ?: "cape.png")
            if (path != null) {
                Oxevy.LOGGER.info("[Cape] Uploaded cape saved to {}", path)
                true
            } else false
        }
        if (result) sendJson(out, 200, buildCapeState()) else sendText(out, 500, "Failed to save cape")
    }

    private fun handleCapeSelect(out: OutputStream, body: String) {
        val json = parseJson(body) ?: run { sendText(out, 400, "Bad JSON"); return }
        val mode = json.get("mode")?.asString
        val index = json.get("index")?.asInt
        val customUrl = json.get("customUrl")?.asString
        val repoIndex = json.get("repoIndex")?.asInt

        onClientThread {
            val mgr = Oxevy.capeManager
            if (repoIndex != null) {
                mgr.selectRepoCape(repoIndex)
            } else {
                if (mode != null) {
                    try { mgr.mode = CapeManager.CapeMode.valueOf(mode) } catch (_: Exception) {}
                }
                if (index != null) {
                    mgr.setActiveUnlockedIndex(index)
                }
                if (customUrl != null) {
                    mgr.setCustomCapeUrl(customUrl)
                }
                mgr.saveSelectionToLauncherFile()
            }
        }
        sendJson(out, 200, onClientThread { buildCapeState() })
    }

    private fun handleCapeRepo(out: OutputStream) {
        val state = onClientThread {
            val mgr = Oxevy.capeManager
            val obj = JsonObject()
            val capesArray = JsonArray()
            for ((i, entry) in mgr.repoCapes.withIndex()) {
                val e = JsonObject()
                e.addProperty("index", i)
                e.addProperty("name", entry.name)
                e.addProperty("url", entry.url)
                capesArray.add(e)
            }
            obj.add("capes", capesArray)
            if (!mgr.isRepoFetched) {
                mgr.fetchRepoCapesAsync()
            }
            obj
        }
        sendJson(out, 200, state)
    }

    private fun handleCapeFile(out: OutputStream, path: String) {
        val relativePath = path.removePrefix("/capes/")
        if (relativePath.contains("..")) { sendText(out, 403, "Forbidden"); return }

        val capesDir = FabricLoader.getInstance().gameDir.resolve("oxevy").resolve("capes")
        val file = capesDir.resolve(relativePath).toFile()
        if (!file.exists() || !file.isFile) { sendText(out, 404, "Not found"); return }

        val bytes = try { Files.readAllBytes(file.toPath()) } catch (e: Exception) { sendText(out, 404, "Not found"); return }
        val mime = if (relativePath.endsWith(".png")) "image/png" else "image/png"
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            appendCors()
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    // ── Multipart parser (raw bytes) ────────────────────────────────────────

    private data class MultipartPart(val name: String, val fileName: String?, val data: ByteArray)

    private fun parseMultipartRaw(raw: ByteArray, boundary: String): List<MultipartPart> {
        val parts = mutableListOf<MultipartPart>()
        val delimiter = "--$boundary"
        val endDelimiter = "--$boundary--"
        val delimiterBytes = delimiter.encodeToByteArray()
        val endBytes = endDelimiter.encodeToByteArray()
        val crlfcrlf = "\r\n\r\n".encodeToByteArray()

        var pos = 0
        while (pos < raw.size) {
            val delimPos = indexOf(raw, delimiterBytes, pos)
            if (delimPos == -1) break
            val partStart = delimPos + delimiterBytes.size
            // skip \r\n after delimiter
            val contentStart = if (partStart < raw.size && raw[partStart] == '\r'.code.toByte() && partStart + 1 < raw.size && raw[partStart + 1] == '\n'.code.toByte())
                partStart + 2 else partStart
            // find next delimiter
            val nextDelim = indexOf(raw, delimiterBytes, contentStart)
                .takeIf { it != -1 }
                ?: indexOf(raw, endBytes, contentStart)
                    .takeIf { it != -1 }
                    ?: break
            // Extract headers and body
            val partBytes = raw.copyOfRange(contentStart, nextDelim)
            val headerEnd = indexOf(partBytes, crlfcrlf, 0)
            if (headerEnd == -1) { pos = nextDelim; continue }
            val headerStr = partBytes.copyOfRange(0, headerEnd).decodeToString()
            val dataStart = headerEnd + 4
            val data = partBytes.copyOfRange(dataStart, partBytes.size)
            // Remove trailing \r\n
            val cleanData = if (data.size >= 2 && data[data.size - 2] == '\r'.code.toByte() && data[data.size - 1] == '\n'.code.toByte())
                data.copyOfRange(0, data.size - 2) else data
            val name = headerStr.substringAfter("name=\"").substringBefore("\"")
            val fileName = headerStr.substringAfter("filename=\"").substringBefore("\"").takeIf { it.isNotEmpty() }
            parts.add(MultipartPart(name, fileName, cleanData))
            pos = nextDelim
        }
        return parts
    }

    private fun indexOf(array: ByteArray, target: ByteArray, start: Int): Int {
        if (target.isEmpty()) return -1
        outer@ for (i in start..array.size - target.size) {
            for (j in target.indices) {
                if (array[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun buildCapeState(): JsonObject {
        val mgr = Oxevy.capeManager
        val obj = JsonObject()
        obj.addProperty("mode", mgr.mode.name)
        obj.addProperty("activeIndex", mgr.activeUnlockedIndex)
        obj.addProperty("customUrl", mgr.customCapeUrl)

        val capesArray = JsonArray()
        for ((i, entry) in mgr.unlockedCapes.withIndex()) {
            val e = JsonObject()
            e.addProperty("index", i)
            e.addProperty("name", entry.name)
            if (entry.localFile != null) {
                e.addProperty("url", "/capes/" + entry.localFile)
            } else if (entry.url != null) {
                e.addProperty("url", entry.url)
            }
            capesArray.add(e)
        }
        obj.add("capes", capesArray)
        return obj
    }

    // ── Static file handler ──────────────────────────────────────────────────

    private fun handleStatic(out: OutputStream, path: String) {
        val rawPath = path.removePrefix("/")
        if (rawPath.contains("..")) { sendText(out, 403, "Forbidden"); return }

        val resourcePath = if (rawPath.isBlank()) "webclickgui/index.html" else "webclickgui/$rawPath"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: run { sendText(out, 404, "Not found: $resourcePath"); return }

        val bytes = stream.use { s ->
            val buf = ByteArrayOutputStream()
            s.copyTo(buf)
            buf.toByteArray()
        }

        // Markup is revalidated on every load, so stamping a content hash into
        // the asset URLs it references is enough to defeat any stale entry CEF
        // still holds from an older jar (the stylesheet used to be served
        // immutable for a year, and CEF's cache lives on disk). Only rewrite
        // when the placeholder is actually there so every other byte survives
        // untouched.
        val body = if (resourcePath.endsWith(".html")) {
            val text = bytes.decodeToString()
            if (text.contains("ASSET_V")) text.replace("ASSET_V", assetVersion).encodeToByteArray() else bytes
        } else bytes

        val mime = contentType(resourcePath)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${body.size}\r\n")
            appendCors()
            append("Cache-Control: ${cacheControl(resourcePath)}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(body)
        out.flush()
    }

    /**
     * First-launch welcome tour, served from {@code webwelcome/}. Same caching
     * contract as the ClickGui page: markup revalidates on every load and its
     * stylesheet URL carries a content hash of that stylesheet.
     */
    private fun handleWelcomeStatic(out: OutputStream, path: String) {
        val rawPath = path.removePrefix("/welcome/")
        if (rawPath.contains("..")) { sendText(out, 403, "Forbidden"); return }

        val resourcePath = if (rawPath.isBlank()) "webwelcome/index.html" else "webwelcome/$rawPath"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: run { sendText(out, 404, "Not found: $resourcePath"); return }

        val bytes = stream.use { s ->
            val buf = ByteArrayOutputStream()
            s.copyTo(buf)
            buf.toByteArray()
        }

        val body = if (resourcePath.endsWith(".html")) {
            val text = bytes.decodeToString()
            if (text.contains("ASSET_V")) {
                text.replace("ASSET_V", assetVersionOf("webwelcome/style.css")).encodeToByteArray()
            } else bytes
        } else bytes

        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: ${contentType(resourcePath)}\r\n")
            append("Content-Length: ${body.size}\r\n")
            appendCors()
            append("Cache-Control: ${cacheControl(resourcePath)}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(body)
        out.flush()
    }

    /** Welcome page's "Get started" (and its Enter/Escape shortcut). */
    private fun handleWelcomeDone(out: OutputStream) {
        onClientThread {
            me.alpha432.oxevy.manager.ConfigManager.markWelcomeSeen()
            me.alpha432.oxevy.features.gui.WebWelcomeScreen.requestDismiss()
        }
        sendJson(out, 200, JsonObject().apply { addProperty("ok", true) })
    }

    private fun handleIslandStatic(out: OutputStream, path: String) {
        val rawPath = path.removePrefix("/island/")
        if (rawPath.contains("..")) { sendText(out, 403, "Forbidden"); return }

        val resourcePath = if (rawPath.isBlank()) "webdynamicisland/index.html" else "webdynamicisland/$rawPath"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: run { sendText(out, 404, "Not found: $resourcePath"); return }

        val bytes = stream.use { s ->
            val buf = ByteArrayOutputStream()
            s.copyTo(buf)
            buf.toByteArray()
        }

        // Markup is revalidated on every load, so stamping a content hash into
        // the asset URLs it references is enough to defeat any stale entry CEF
        // still holds from an older jar (the stylesheet used to be served
        // immutable for a year, and CEF's cache lives on disk). The hash is of
        // THIS page's stylesheet - not the clickgui one - so island-only edits
        // still bust the cache. Only rewrite when the placeholder is actually
        // there so every other byte survives untouched.
        val body = if (resourcePath.endsWith(".html")) {
            val text = bytes.decodeToString()
            if (text.contains("ASSET_V")) {
                text.replace("ASSET_V", assetVersionOf("webdynamicisland/style.css")).encodeToByteArray()
            } else bytes
        } else bytes

        val mime = contentType(resourcePath)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${body.size}\r\n")
            appendCors()
            append("Cache-Control: ${cacheControl(resourcePath)}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private fun handleMainMenuStatic(out: OutputStream, path: String) {
        val rawPath = path.removePrefix("/mainmenu/")
        if (rawPath.contains("..")) { sendText(out, 403, "Forbidden"); return }

        val resourcePath = if (rawPath.isBlank()) "webmainmenu/index.html" else "webmainmenu/$rawPath"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: run { sendText(out, 404, "Not found: $resourcePath"); return }

        val bytes = stream.use { s ->
            val buf = ByteArrayOutputStream()
            s.copyTo(buf)
            buf.toByteArray()
        }

        val mime = contentType(resourcePath)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            appendCors()
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    /** Serves webshared/ - the ConfigPanel component + styles, shared by the
        main menu overlay and the ClickGui "Configs" tab. */
    private fun handleSharedStatic(out: OutputStream, path: String) {
        val rawPath = path.removePrefix("/shared/")
        if (rawPath.contains("..")) { sendText(out, 403, "Forbidden"); return }

        val resourcePath = "webshared/$rawPath"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: run { sendText(out, 404, "Not found: $resourcePath"); return }

        val bytes = stream.use { s ->
            val buf = ByteArrayOutputStream()
            s.copyTo(buf)
            buf.toByteArray()
        }

        val mime = contentType(resourcePath)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            appendCors()
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun handlePauseMenuStatic(out: OutputStream, path: String) {
        val rawPath = path.removePrefix("/pausemenu/")
        if (rawPath.contains("..")) { sendText(out, 403, "Forbidden"); return }

        val resourcePath = if (rawPath.isBlank()) "webpausemenu/index.html" else "webpausemenu/$rawPath"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: run { sendText(out, 404, "Not found: $resourcePath"); return }

        val bytes = stream.use { s ->
            val buf = ByteArrayOutputStream()
            s.copyTo(buf)
            buf.toByteArray()
        }

        val mime = contentType(resourcePath)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            appendCors()
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    /**
     * GET /api/mainmenu/profile
     *
     * Signed-in account name + uuid for the main-menu / pause-menu skin card.
     * Prefers the live player profile when in a world, and falls back to
     * Minecraft.getGameProfile() – which is populated on the title screen where
     * mc.player is null. Previously this returned early without writing anything
     * when there was no player, so the browser saw the socket close and reported
     * the generic "Unable to load profile" error.
     */
    private fun handleProfile(out: OutputStream) {
        val body = try {
            val mc = Minecraft.getInstance()
            val gameProfile = mc.player?.gameProfile ?: mc.gameProfile
            if (gameProfile == null) {
                JsonObject().apply {
                    addProperty("username", "Not logged in")
                    addProperty("uuid", "")
                    addProperty("discriminator", "")
                    addProperty("available", false)
                }
            } else {
                JsonObject().apply {
                    addProperty("username", gameProfile.name ?: "Unknown")
                    addProperty("uuid", gameProfile.id?.toString() ?: "")
                    // Modern accounts carry no "#1234" discriminator; kept for UI parity.
                    addProperty("discriminator", "")
                    addProperty("available", true)
                }
            }
        } catch (e: Exception) {
            JsonObject().apply {
                addProperty("username", "Profile unavailable")
                addProperty("uuid", "")
                addProperty("discriminator", "")
                addProperty("available", false)
                addProperty("error", e.message ?: "unknown error")
            }
        }
        sendJson(out, 200, body)
    }

    // ── Now playing ─────────────────────────────────────────────────────────
    //
    // Backed entirely by MusicPlayerHudModule, which already polls the system
    // media player on its own daemon thread. These endpoints add no polling of
    // their own - they only read the module's atomics - so the cost of the main
    // menu's "now playing" popup is one tiny JSON read per poll while the menu
    // is open, and nothing at all once it closes.

    private fun musicNowPlaying(): MusicPlayerHudModule.NowPlaying? = try {
        Oxevy.moduleManager.getModuleByClass(MusicPlayerHudModule::class.java)?.nowPlaying()
    } catch (e: Exception) {
        null
    }

    /**
     * Identity of the current track's artwork.
     *
     * <p>Deliberately not just the art URL: some MPRIS players (Plasma's browser
     * integration, for one) rewrite a single temp file for every track, so the
     * URL is identical across two completely different covers. Keying on the
     * track as well means a new song always misses both this cache and the
     * page's own, instead of showing the previous song's cover.
     */
    private fun musicArtKey(now: MusicPlayerHudModule.NowPlaying?): String =
        if (now == null || now.artUrl.isEmpty()) "" else "${now.artist}|${now.title}|${now.artUrl}"

    private fun musicArtId(key: String): String =
        if (key.isEmpty()) "" else Integer.toHexString(key.hashCode())

    private fun handleMusicState(out: OutputStream) {
        val now = musicNowPlaying()
        val artKey = musicArtKey(now)
        // Start decoding the cover now rather than when the page asks for it, so
        // the popup's artwork is usually already cached by then.
        warmMusicArt(artKey, now?.artUrl ?: "")
        val body = JsonObject().apply {
            addProperty("available", now != null && now.available)
            addProperty("hasTrack", now?.hasTrack ?: false)
            addProperty("playing", now?.playing ?: false)
            addProperty("title", now?.title ?: "")
            addProperty("artist", now?.artist ?: "")
            addProperty("status", now?.status ?: "")
            addProperty("positionMs", (now?.positionUs ?: 0L) / 1000L)
            addProperty("lengthMs", (now?.lengthUs ?: 0L) / 1000L)
            addProperty("artId", musicArtId(artKey))
        }
        sendJson(out, 200, body)
    }

    private fun handleMusicArt(out: OutputStream) {
        val now = musicNowPlaying()
        val key = musicArtKey(now)
        val url = now?.artUrl ?: ""
        if (key.isEmpty() || url.isEmpty()) {
            sendText(out, 404, "No artwork")
            return
        }

        var art = musicArtCache[key]
        if (art == null) {
            art = encodeMusicArt(url)
            if (art != null) {
                // Bounded: one entry per track change would otherwise sit on a
                // few hundred KB of PNG per song for the whole session.
                if (musicArtCache.size >= 16) musicArtCache.clear()
                musicArtCache[key] = art
            }
        }
        if (art == null) {
            sendText(out, 404, "No artwork")
            return
        }

        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: image/png\r\n")
            append("Content-Length: ${art.size}\r\n")
            appendCors()
            // The page keys this by artId, so a repeat request for the same
            // cover is answered from its own cache while a track plays on.
            append("Cache-Control: max-age=600\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(art)
        out.flush()
    }

    /** Decode the cover and re-encode it as PNG, or null if it cannot be read. */
    private fun encodeMusicArt(url: String): ByteArray? = try {
        val image = AlbumArtLoader.get(url)
        if (image == null) {
            null
        } else {
            val encoded = ByteArrayOutputStream()
            if (ImageIO.write(image, "png", encoded)) encoded.toByteArray() else null
        }
    } catch (e: Exception) {
        null
    }

    private fun warmMusicArt(key: String, url: String) {
        if (key.isEmpty() || url.isEmpty() || key == musicArtWarmed.get()) return
        musicArtWarmed.set(key)
        executor.submit {
            // Publish into the cache, so the page's request for the bytes is a
            // map lookup instead of another fetch plus another PNG encode.
            val bytes = encodeMusicArt(url)
            if (bytes != null) {
                if (musicArtCache.size >= 16) musicArtCache.clear()
                musicArtCache[key] = bytes
            }
        }
    }

    private fun handleMainMenuNavigate(out: OutputStream, body: String) {
        val action = parseJson(body)?.get("action")?.asString ?: ""
        onClientThread {
            val mc = Minecraft.getInstance()
            // Use reflection to bypass Kotlin's nullability checks for platform types
            val ns: Any? = null
            when (action) {
                "singleplayer" -> {
                    val cls = net.minecraft.client.gui.screens.worldselection.SelectWorldScreen::class.java
                    val ctor = cls.getConstructor(net.minecraft.client.gui.screens.Screen::class.java)
                    mc.setScreen(ctor.newInstance(ns))
                }
                "multiplayer" -> {
                    val cls = net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen::class.java
                    val ctor = cls.getConstructor(net.minecraft.client.gui.screens.Screen::class.java)
                    mc.setScreen(ctor.newInstance(ns))
                }
                "worlds" -> {
                    val cls = net.minecraft.client.gui.screens.worldselection.SelectWorldScreen::class.java
                    val ctor = cls.getConstructor(net.minecraft.client.gui.screens.Screen::class.java)
                    mc.setScreen(ctor.newInstance(ns))
                }
                "resourcepacks" -> {
                    // Previously this opened OptionsScreen - the same thing
                    // "options" does - so the Resource Packs entry just dumped you
                    // on the general options page.
                    //
                    // This mirrors how vanilla wires it up in
                    // OptionsScreen.applyPacks: hand the screen the live
                    // PackRepository, and on "Done" persist the selection back
                    // into Options and return to the screen the button was on.
                    // PackSelectionScreen's own Watcher handles reloading the
                    // resource manager, so packs apply as you toggle them.
                    val packs = net.minecraft.client.gui.screens.packs.PackSelectionScreen(
                        mc.resourcePackRepository,
                        java.util.function.Consumer<net.minecraft.server.packs.repository.PackRepository> { repo ->
                            mc.options.updateResourcePacks(repo)
                            // Was opened from the Oxevy web main menu, so go back
                            // there rather than to the options screen.
                            me.alpha432.oxevy.features.gui.WebMainMenuScreen.open()
                        },
                        mc.resourcePackDirectory,
                        net.minecraft.network.chat.Component.translatable("resourcePack.title")
                    )
                    mc.setScreen(packs)
                }
                "options" -> {
                    val cls = net.minecraft.client.gui.screens.options.OptionsScreen::class.java
                    val ctor = cls.getConstructor(net.minecraft.client.gui.screens.Screen::class.java, net.minecraft.client.Options::class.java)
                    mc.setScreen(ctor.newInstance(ns, mc.options))
                }
                "credits" -> {
                    val cls = net.minecraft.client.gui.screens.CreditsAndAttributionScreen::class.java
                    val ctor = cls.getConstructor(net.minecraft.client.gui.screens.Screen::class.java)
                    mc.setScreen(ctor.newInstance(ns))
                }
                "relog" -> {
                    // Disconnect from current server and return to multiplayer screen
                    val conn = mc.getConnection()
                    if (conn != null) {
                        // Disconnect using reflection since the type might not expose disconnect directly
                        try {
                            val method = conn.javaClass.getMethod("disconnect", net.minecraft.network.chat.Component::class.java)
                            method.invoke(conn, net.minecraft.network.chat.Component.literal("Relogging..."))
                        } catch (e: Exception) {
                            // Connection will be closed when we switch screens
                        }
                    }
                    // Open multiplayer screen for reconnection (this will also disconnect if needed)
                    val cls = net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen::class.java
                    val ctor = cls.getConstructor(net.minecraft.client.gui.screens.Screen::class.java)
                    mc.setScreen(ctor.newInstance(ns))
                }
                "quit" -> mc.execute(mc::stop)
                "clickgui" -> {
                    mc.execute {
                        me.alpha432.oxevy.features.modules.client.ClickGuiModule.getInstance().enable()
                    }
                }
                "accounts" -> {
                    val cls = me.alpha432.oxevy.features.gui.WebAccountsScreen::class.java
                    val ctor = cls.getConstructor(net.minecraft.client.gui.screens.Screen::class.java)
                    mc.setScreen(ctor.newInstance(ns))
                }
                "cosmetics" -> {
                    val cls = me.alpha432.oxevy.features.gui.WebCosmeticsScreen::class.java
                    val ctor = cls.getConstructor(net.minecraft.client.gui.screens.Screen::class.java)
                    mc.setScreen(ctor.newInstance(ns))
                }
                else -> {}
            }
        }
        sendJson(out, 200, JsonObject().apply { addProperty("ok", true) })
    }

    // ── State builder ───────────────────────────────────────────────────────

    private fun buildState(): JsonObject {
        val root = JsonObject()
        root.addProperty("name", "Oxevy")
        root.addProperty("version", "1.0")
        root.addProperty("url", baseUrl())
        root.addProperty("activeCount",
            Oxevy.moduleManager.modules.count { !it.hidden && it.isEnabled })

        val categoriesArray = JsonArray()
        for (category in Module.Category.values()) {
            val catObj = JsonObject()
            val modules = Oxevy.moduleManager.getModulesByCategory(category).filter { !it.hidden }
            catObj.addProperty("key", category.name)
            catObj.addProperty("name", category.name
                .lowercase()
                .replaceFirstChar { it.uppercaseChar() })
            catObj.addProperty("icon", category.icon)
            catObj.addProperty("shortName", category.shortName)
            catObj.addProperty("enabledCount", modules.count { it.isEnabled })

            val moduleArray = JsonArray()
            for (module in modules) moduleArray.add(buildModuleJson(module))
            catObj.add("modules", moduleArray)
            categoriesArray.add(catObj)
        }
        root.add("categories", categoriesArray)
        root.add("config", buildClickGuiThemeConfig())
        return root
    }

    /**
     * GET /api/island/state payload.
     *
     * Contract consumed by webdynamicisland/index.html:
     *   { fps, ping, memory:{used,max,percent},
     *     player:{x,y,z,biome}, world:{time,dimension},
     *     config:{showFps,...} }
     *
     * Every MC call below is guarded: this runs on a raw socket worker and must
     * never throw, or the CEF page's fetch() just hangs until the socket dies.
     */
    private fun buildIslandState(): JsonObject = try {
        buildIslandStateBody()
    } catch (e: Throwable) {
        Oxevy.LOGGER.warn("[WebGui] Dynamic Island state failed: {}", e.message)
        islandFallbackState()
    }

    private fun buildIslandStateBody(): JsonObject {
        val mc = Minecraft.getInstance()
        val obj = JsonObject()

        // ── fps ────────────────────────────────────────────────────────────
        obj.addProperty("fps", try { mc.getFps() } catch (_: Throwable) { 0 })

        // ── ping (ms, 0 when unknown) ─────────────────────────────────────
        obj.addProperty("ping", try {
            val p = mc.player
            val conn = mc.getConnection()
            if (p != null && conn != null) conn.getPlayerInfo(p.uuid)?.latency ?: 0 else 0
        } catch (_: Throwable) { 0 })

        // ── memory ────────────────────────────────────────────────────────
        val runtime = Runtime.getRuntime()
        val usedMem = runtime.totalMemory() - runtime.freeMemory()
        val maxMem = runtime.maxMemory()
        val memObj = JsonObject()
        memObj.addProperty("used", usedMem)
        memObj.addProperty("max", maxMem)
        memObj.addProperty("percent", if (maxMem > 0) (usedMem.toDouble() / maxMem * 100).toInt() else 0)
        obj.add("memory", memObj)

        val player = try { mc.player } catch (_: Throwable) { null }
        val level = try { mc.level } catch (_: Throwable) { null }

        // ── player { x, y, z, biome } ─────────────────────────────────────
        val playerObj = JsonObject()
        if (player != null) {
            playerObj.addProperty("x", try { player.x } catch (_: Throwable) { 0.0 })
            playerObj.addProperty("y", try { player.y } catch (_: Throwable) { 0.0 })
            playerObj.addProperty("z", try { player.z } catch (_: Throwable) { 0.0 })
        } else {
            playerObj.addProperty("x", 0.0)
            playerObj.addProperty("y", 0.0)
            playerObj.addProperty("z", 0.0)
        }
        playerObj.addProperty("biome", try {
            if (player == null || level == null) "unknown"
            else biomeIdOf(level.getBiomeManager()
                .getBiome(BlockPos(player.x.toInt(), player.y.toInt(), player.z.toInt())))
        } catch (_: Throwable) { "unknown" })
        obj.add("player", playerObj)

        // ── world { time, dimension } ─────────────────────────────────────
        val worldObj = JsonObject()
        worldObj.addProperty("time", try {
            if (level == null) "00:00" else formatIslandTime(level.getDayTime())
        } catch (_: Throwable) { "00:00" })
        worldObj.addProperty("dimension", dimensionIdOf(level))
        obj.add("world", worldObj)

        // ── config: which metrics the user enabled in DynamicIslandHudModule ──
        obj.add("config", buildIslandConfig())

        return obj
    }

    /** Shape-preserving all-zero payload used if anything above blows up. */
    private fun islandFallbackState(): JsonObject {
        val runtime = Runtime.getRuntime()
        val memObj = JsonObject()
        memObj.addProperty("used", 0L)
        memObj.addProperty("max", runtime.maxMemory())
        memObj.addProperty("percent", 0)

        val playerObj = JsonObject()
        playerObj.addProperty("x", 0.0)
        playerObj.addProperty("y", 0.0)
        playerObj.addProperty("z", 0.0)
        playerObj.addProperty("biome", "unknown")

        val worldObj = JsonObject()
        worldObj.addProperty("time", "00:00")
        worldObj.addProperty("dimension", "unknown")

        val obj = JsonObject()
        obj.addProperty("fps", 0)
        obj.addProperty("ping", 0)
        obj.add("memory", memObj)
        obj.add("player", playerObj)
        obj.add("world", worldObj)
        obj.add("config", buildIslandConfig())
        return obj
    }

    // ── Dynamic Island helpers ─────────────────────────────────────────────

    /** One boolean metric flag of DynamicIslandHudModule and how to read it. */
    private data class IslandFlag(val json: String, val field: String, val settingName: String)

    private val ISLAND_FLAGS = listOf(
        IslandFlag("showFps",       "showFps",       "Show FPS"),
        IslandFlag("showPing",      "showPing",      "Show Ping"),
        IslandFlag("showMemory",    "showMemory",    "Show Memory"),
        IslandFlag("showCoords",    "showCoords",    "Show Coordinates"),
        IslandFlag("showTime",      "showTime",      "Show Time"),
        IslandFlag("showBiome",     "showBiome",     "Show Biome"),
        IslandFlag("showDimension", "showDimension", "Show Dimension")
    )

    /**
     * Minecraft tick count -> "HH:MM".
     * tick 0 == 06:00, 24000 ticks == one full day, so 1000 ticks == 1 hour.
     * Verified: 0->06:00, 6000->12:00, 18000->00:00, 23000->05:00.
     */
    private fun formatIslandTime(ticks: Long): String {
        val hours = (ticks / 1000.0 + 6.0) % 24.0
        val totalMinutes = ((hours + 24.0) % 24.0 * 60.0).roundToInt()
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return String.format(Locale.ROOT, "%02d:%02d", h, m)
    }

    /**
     * Holder<Biome> -> "minecraft:plains".
     * Holder.toString() yields "Holder(ResourceKey[minecraft:plains])" – useless
     * for the HUD, so unwrap the registry key and use its Identifier instead.
     */
    private fun biomeIdOf(biome: Holder<*>?): String {
        if (biome == null) return "unknown"
        return try {
            biome.unwrapKey().map { it.identifier().toString() }.orElse("unknown")
        } catch (_: Throwable) {
            "unknown"
        }
    }

    /**
     * Level -> "minecraft:overworld".
     * Level.dimension() is a ResourceKey<Level>; ResourceKey has identifier(),
     * NOT location(), in 1.21.11. Identifier.toString() is "namespace:path".
     */
    private fun dimensionIdOf(level: Level?): String {
        if (level == null) return "unknown"
        return try {
            level.dimension().identifier().toString()
        } catch (_: Throwable) {
            "unknown"
        }
    }

    /** A Setting<Boolean> (or a raw Boolean) -> Boolean, defaulting to enabled. */
    private fun settingBoolean(raw: Any?): Boolean = when (raw) {
        is Boolean    -> raw
        is Setting<*> -> (raw.value as? Boolean) ?: true
        else          -> true
    }

    /**
     * Config block for GET /api/state: the persisted ClickGui theme plus the
     * full theme catalogue, so the page never has to hardcode the list.
     *
     * Runs on a raw socket worker thread, so nothing here may throw — an
     * exception would break the whole page. A missing module/setting degrades
     * to the GLASS default.
     */
    private fun buildClickGuiThemeConfig(): JsonObject {
        val cfg = JsonObject()

        val theme = try {
            Oxevy.moduleManager.getModuleByClass(ClickGuiModule::class.java)
                ?.theme?.value
                ?: ClickGuiModule.WebTheme.GLASS
        } catch (_: Throwable) {
            ClickGuiModule.WebTheme.GLASS
        }

        cfg.addProperty("theme", theme.name)
        cfg.addProperty("layout", theme.layout)

        // Built from the enum itself so the catalogue can never drift.
        val themesArray = JsonArray()
        for (t in ClickGuiModule.WebTheme.entries) {
            val themeObj = JsonObject()
            themeObj.addProperty("key", t.name)
            themeObj.addProperty("name", t.displayName)
            themeObj.addProperty("layout", t.layout)
            themeObj.addProperty("swatch", t.swatch)
            themesArray.add(themeObj)
        }
        cfg.add("themes", themesArray)
        return cfg
    }

    private fun buildIslandConfig(): JsonObject {
        val cfg = JsonObject()
        val module = try {
            Oxevy.moduleManager?.getModuleByName("DynamicIsland")
        } catch (_: Throwable) {
            null
        }
        for (flag in ISLAND_FLAGS) cfg.addProperty(flag.json, islandFlagValue(module, flag))
        return cfg
    }

    /**
     * Read one flag off the DynamicIsland module. Prefers the declared
     * public Setting<Boolean> field; falls back to matching the Setting by its
     * display name. Missing/unreadable -> true, so a metric never silently
     * disappears just because the module failed to register.
     */
    private fun islandFlagValue(module: Module?, flag: IslandFlag): Boolean {
        if (module == null) return true
        val clazz = module.javaClass
        val raw = try {
            clazz.getField(flag.field).get(module)
        } catch (_: Throwable) {
            try {
                val f = clazz.getDeclaredField(flag.field)
                f.isAccessible = true
                f.get(module)
            } catch (_: Throwable) {
                null
            }
        }
        if (raw != null) return settingBoolean(raw)
        return try {
            module.settings.firstOrNull { it.name.equals(flag.settingName, true) }
                ?.let { settingBoolean(it) } ?: true
        } catch (_: Throwable) {
            true
        }
    }

    private fun buildModuleJson(module: Module): JsonObject {
        val obj = JsonObject()
        obj.addProperty("id",          module.name)
        obj.addProperty("name",        module.name)
        obj.addProperty("description", module.description ?: "")
        obj.addProperty("category",    module.category.name)
        obj.addProperty("enabled",     module.isEnabled)

        // Keybind
        val bindObj = JsonObject()
        try {
            val bind = module.getBind()
            bindObj.addProperty("key",   bind?.getKey() ?: -1)
            bindObj.addProperty("label", bind?.toString() ?: "None")
        } catch (_: Exception) {
            bindObj.addProperty("key", -1)
            bindObj.addProperty("label", "None")
        }
        obj.add("bind", bindObj)

        // Settings
        val settingsArray = JsonArray()
        for (s in module.settings) {
            if (s.name.equals("Enabled", true) || s.name.equals("DisplayName", true)) continue
            try { settingsArray.add(buildSettingJson(s)) } catch (_: Exception) {}
        }
        obj.add("settings", settingsArray)
        return obj
    }

    private fun buildSettingJson(setting: Setting<*>): JsonObject {
        val obj   = JsonObject()
        val value = setting.value
        val type  = settingType(setting)

        obj.addProperty("id",   setting.name)
        obj.addProperty("name", setting.name)
        obj.addProperty("type", type)
        obj.addProperty("description", setting.description ?: "")

        when (type) {
            "boolean" -> obj.addProperty("value", value as Boolean)
            "number"  -> {
                obj.addProperty("value", (value as Number).toDouble())
                setting.min?.let { obj.addProperty("min", (it as Number).toDouble()) }
                setting.max?.let { obj.addProperty("max", (it as Number).toDouble()) }
                obj.addProperty("step",
                    if (value is Int || value is Long || value is Short || value is Byte) 1 else 0.1)
            }
            "string"  -> obj.addProperty("value", value as String)
            "color"   -> obj.add("value", buildColorJson(value as Color))
            "bind"    -> obj.add("value", buildBindJson(value as Bind))
            "enum"    -> {
                val enumValue = value as Enum<*>
                obj.addProperty("value", enumValue.name)
                val options = JsonArray()
                enumValue.javaClass.enumConstants.forEach { options.add((it as Enum<*>).name) }
                obj.add("options", options)
            }
        }
        return obj
    }

    private fun buildColorJson(color: Color): JsonObject {
        val o = JsonObject()
        o.addProperty("r", color.red)
        o.addProperty("g", color.green)
        o.addProperty("b", color.blue)
        o.addProperty("a", color.alpha)
        return o
    }

    private fun buildBindJson(bind: Bind): JsonObject {
        val o = JsonObject()
        o.addProperty("key",   bind.key)
        o.addProperty("label", bind.toString())
        return o
    }

    private fun settingType(setting: Setting<*>): String = when (val v = setting.value) {
        is Boolean -> "boolean"
        is Number  -> if (setting.min != null) "number" else "unknown"
        is String  -> "string"
        is Color   -> "color"
        is Bind    -> "bind"
        is Enum<*> -> "enum"
        else       -> "unknown"
    }

    // ── Setting appliers ────────────────────────────────────────────────────

    private fun applySettingUpdate(moduleId: String, settingName: String, value: JsonElement): String? {
        val module = Oxevy.moduleManager.getModuleByName(moduleId)
            ?: return "Unknown module: $moduleId"
        val setting = module.settings.firstOrNull { it.name.equals(settingName, true) }
            ?: return "Unknown setting: $settingName in $moduleId"
        return try {
            when (settingType(setting)) {
                "boolean" -> @Suppress("UNCHECKED_CAST") (setting as Setting<Boolean>).setValue(value.asBoolean)
                "number"  -> applyNumericSetting(setting, value.asDouble)
                "string"  -> @Suppress("UNCHECKED_CAST") (setting as Setting<String>).setValue(value.asString)
                "color"   -> @Suppress("UNCHECKED_CAST") applyColorSetting(setting as Setting<Color>, value.asJsonObject)
                "bind"    -> @Suppress("UNCHECKED_CAST") applyBindSetting(setting as Setting<Bind>, value)
                "enum"    -> applyEnumSetting(setting, value.asString)
                else      -> return "Unsupported setting type"
            }
            null
        } catch (e: Exception) {
            "Failed to update ${module.name}.${setting.name}: ${e.message}"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyEnumSetting(setting: Setting<*>, requestedValue: String) {
        val current = setting.value as Enum<*>
        val next = current.javaClass.enumConstants
            .map { it as Enum<*> }
            .firstOrNull { it.name.equals(requestedValue, true) }
            ?: throw IllegalArgumentException("Unknown enum value: $requestedValue")
        (setting as Setting<Enum<*>>).setValue(next)
    }

    private fun applyNumericSetting(setting: Setting<*>, raw: Double) {
        when (setting.value) {
            is Int    -> @Suppress("UNCHECKED_CAST") (setting as Setting<Int>).setValue(raw.roundToInt())
            is Float  -> @Suppress("UNCHECKED_CAST") (setting as Setting<Float>).setValue(raw.toFloat())
            is Double -> @Suppress("UNCHECKED_CAST") (setting as Setting<Double>).setValue(raw)
            is Long   -> @Suppress("UNCHECKED_CAST") (setting as Setting<Long>).setValue(raw.roundToLong())
            is Short  -> @Suppress("UNCHECKED_CAST") (setting as Setting<Short>).setValue(raw.roundToInt().toShort())
            is Byte   -> @Suppress("UNCHECKED_CAST") (setting as Setting<Byte>).setValue(raw.roundToInt().toByte())
            else      -> throw IllegalArgumentException("Unsupported number type")
        }
    }

    private fun applyColorSetting(setting: Setting<Color>, value: JsonObject) {
        setting.setValue(Color(
            value.get("r").asInt.coerceIn(0, 255),
            value.get("g").asInt.coerceIn(0, 255),
            value.get("b").asInt.coerceIn(0, 255),
            value.get("a").asInt.coerceIn(0, 255)
        ))
    }

    private fun applyBindSetting(setting: Setting<Bind>, value: JsonElement) {
        val key = when {
            value.isJsonPrimitive -> value.asInt
            value.isJsonObject && value.asJsonObject.has("key") -> value.asJsonObject["key"].asInt
            else -> throw IllegalArgumentException("Bind requires a key code")
        }
        setting.setValue(if (key < 0) Bind.none() else Bind(key))
    }

    // ── HTTP helpers ────────────────────────────────────────────────────────

    private fun sendJson(out: OutputStream, status: Int, body: JsonObject) {
        val bytes = gson.toJson(body).toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status ${reasonPhrase(status)}\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            appendCors()
            append("Cache-Control: no-store\r\nConnection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun sendText(out: OutputStream, status: Int, message: String) {
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status ${reasonPhrase(status)}\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            appendCors()
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun sendCors(out: OutputStream) {
        val header = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            appendCors()
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    private fun StringBuilder.appendCors() {
        append("Access-Control-Allow-Origin: *\r\n")
        append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        append("Access-Control-Allow-Headers: Content-Type\r\n")
    }

    private fun contentType(path: String): String = when {
        path.endsWith(".html") -> "text/html; charset=utf-8"
        path.endsWith(".js")   -> "application/javascript; charset=utf-8"
        path.endsWith(".jsx")  -> "application/javascript; charset=utf-8"
        path.endsWith(".css")  -> "text/css; charset=utf-8"
        path.endsWith(".ttf")  -> "font/ttf"
        path.endsWith(".woff") -> "font/woff"
        path.endsWith(".woff2") -> "font/woff2"
        path.endsWith(".png")  -> "image/png"
        path.endsWith(".ico")  -> "image/x-icon"
        else                   -> "application/octet-stream"
    }

    /**
     * Short content hash of a stylesheet, substituted for the ASSET_V
     * placeholder in served markup so asset URLs change whenever the sheet
     * does. Kills the class of bug where a jar update keeps showing the
     * previous release's CSS out of CEF's on-disk cache. Cached per resource.
     */
    private fun assetVersionOf(resource: String): String = assetVersions.computeIfAbsent(resource) { key ->
        try {
            val data = javaClass.classLoader.getResourceAsStream(key)?.use { s -> s.readBytes() }
            if (data == null) "0"
            else java.security.MessageDigest.getInstance("SHA-1").digest(data).take(4)
                .joinToString("") { b -> "%02x".format(b) }
        } catch (_: Throwable) {
            "0"
        }
    }

    private val assetVersions = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val assetVersion: String by lazy { assetVersionOf("webclickgui/style.css") }

    private fun cacheControl(path: String): String = when {
        // Markup and code must be revalidated on every load: the files are tiny
        // and local, and CEF keeps a persistent cache on disk (config/mcef-modern/
        // cache) keyed by URL -- the port is stable (8765+), so an immutable
        // stylesheet would keep serving the version from a previous jar and new
        // widgets would render unstyled after an update.
        path.endsWith(".html") -> "no-cache"
        path.endsWith(".css") || path.endsWith(".js") -> "no-cache"
        else -> "public, max-age=31536000, immutable"
    }

    private fun reasonPhrase(status: Int) = when (status) {
        200 -> "OK"; 204 -> "No Content"; 304 -> "Not Modified"; 400 -> "Bad Request"
        403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
        else -> "Unknown"
    }

    private fun parseJson(text: String): JsonObject? {
        if (text.isBlank()) return null
        return try { JsonParser.parseString(text).asJsonObject } catch (_: Exception) { null }
    }

    private fun baseUrl() = "http://127.0.0.1:$activePort/"

    // ── MC thread dispatch ──────────────────────────────────────────────────

    private fun <T> onClientThread(action: () -> T): T {
        val mc = Minecraft.getInstance()
        if (mc.isSameThread()) return action()
        val future = CompletableFuture<T>()
        mc.execute {
            try { future.complete(action()) }
            catch (e: Throwable) { future.completeExceptionally(e) }
        }
        return future.get(5, TimeUnit.SECONDS)
    }

    // ── Key code helper (unchanged from original) ───────────────────────────

    fun glfwKeyFromCode(code: String): Int? {
        if (code.startsWith("Key") && code.length == 4) {
            val letter = code[3].uppercaseChar()
            return if (letter in 'A'..'Z') GLFW.GLFW_KEY_A + (letter.code - 'A'.code) else null
        }
        if (code.startsWith("Digit") && code.length == 6) {
            val digit = code[5]
            return if (digit in '0'..'9') GLFW.GLFW_KEY_0 + (digit.code - '0'.code) else null
        }
        return when (code.lowercase(Locale.ROOT)) {
            "space"        -> GLFW.GLFW_KEY_SPACE
            "escape"       -> GLFW.GLFW_KEY_ESCAPE
            "enter", "numpadenter" -> GLFW.GLFW_KEY_ENTER
            "tab"          -> GLFW.GLFW_KEY_TAB
            "backspace"    -> GLFW.GLFW_KEY_BACKSPACE
            "delete"       -> GLFW.GLFW_KEY_DELETE
            "insert"       -> GLFW.GLFW_KEY_INSERT
            "arrowleft"    -> GLFW.GLFW_KEY_LEFT
            "arrowright"   -> GLFW.GLFW_KEY_RIGHT
            "arrowup"      -> GLFW.GLFW_KEY_UP
            "arrowdown"    -> GLFW.GLFW_KEY_DOWN
            "shiftleft"    -> GLFW.GLFW_KEY_LEFT_SHIFT
            "shiftright"   -> GLFW.GLFW_KEY_RIGHT_SHIFT
            "controlleft"  -> GLFW.GLFW_KEY_LEFT_CONTROL
            "controlright" -> GLFW.GLFW_KEY_RIGHT_CONTROL
            "altleft"      -> GLFW.GLFW_KEY_LEFT_ALT
            "altright"     -> GLFW.GLFW_KEY_RIGHT_ALT
            "minus"        -> GLFW.GLFW_KEY_MINUS
            "equal"        -> GLFW.GLFW_KEY_EQUAL
            "bracketleft"  -> GLFW.GLFW_KEY_LEFT_BRACKET
            "bracketright" -> GLFW.GLFW_KEY_RIGHT_BRACKET
            "backslash"    -> GLFW.GLFW_KEY_BACKSLASH
            "semicolon"    -> GLFW.GLFW_KEY_SEMICOLON
            "quote"        -> GLFW.GLFW_KEY_APOSTROPHE
            "comma"        -> GLFW.GLFW_KEY_COMMA
            "period"       -> GLFW.GLFW_KEY_PERIOD
            "slash"        -> GLFW.GLFW_KEY_SLASH
            "backquote"    -> GLFW.GLFW_KEY_GRAVE_ACCENT
            else           -> null
        }
    }
    /**
     * Reads the live account nick without assuming a particular Minecraft
     * accessor exists. Reaching for `mc.user` unguarded (as this did before)
     * meant a null there threw before the response was written, and the page
     * silently fell back to its empty state. Each step falls through to the
     * next instead.
     */
    private fun currentAccountName(): String {
        try {
            Minecraft.getInstance().user?.name?.takeIf { it.isNotEmpty() }?.let { return it }
        } catch (e: Throwable) {
            Oxevy.LOGGER.debug("[WebGui] mc.user unavailable: {}", e.message)
        }
        return try {
            onClientThread { Minecraft.getInstance().user?.name ?: "" }
        } catch (e: Throwable) {
            Oxevy.LOGGER.debug("[WebGui] current account name unresolved: {}", e.message)
            ""
        }
    }

    private fun handleAccountsList(out: OutputStream) {
        val obj = JsonObject()
        val arr = JsonArray()
        try {
            Oxevy.accountManager?.getAccounts()?.forEach { acc -> if (acc != null) arr.add(acc) }
        } catch (e: Throwable) {
            Oxevy.LOGGER.warn("[WebGui] accounts list read failed: {}", e.message)
        }
        val current = currentAccountName()
        // Ensure the currently-playing account is always visible in the list,
        // even if it was never explicitly added via the "Add" button.
        if (current.isNotEmpty() && arr.none { it.asString == current }) {
            val fresh = JsonArray()
            fresh.add(current)
            arr.forEach { fresh.add(it) }
            obj.add("accounts", fresh)
        } else {
            obj.add("accounts", arr)
        }
        obj.addProperty("current", current)
        sendJson(out, 200, obj)
    }

    private fun handleAccountsAdd(out: OutputStream, body: String) {
        val name = parseJson(body)?.get("username")?.asString ?: ""
        respondAccountsAction(out, "add", name) { Oxevy.accountManager.addAccount(name) }
    }

    private fun handleAccountsRemove(out: OutputStream, body: String) {
        val name = parseJson(body)?.get("username")?.asString ?: ""
        respondAccountsAction(out, "remove", name) { Oxevy.accountManager.removeAccount(name) }
    }

    private fun handleAccountsSwitch(out: OutputStream, body: String) {
        val name = parseJson(body)?.get("username")?.asString ?: ""
        respondAccountsAction(out, "switch", name) { Oxevy.accountManager.switchAccount(name) }
    }

    /**
     * Runs a mutating account action on the client thread and always replies
     * with a parseable body, so the web UI can surface a real message instead
     * of hanging on a socket the server already abandoned.
     */
    private fun respondAccountsAction(
        out: OutputStream,
        action: String,
        name: String,
        block: () -> Unit
    ) {
        val ok = try {
            if (name.isBlank()) {
                Oxevy.LOGGER.warn("[WebGui] account {} rejected: empty username", action)
                false
            } else {
                onClientThread { block() }
                true
            }
        } catch (e: Throwable) {
            Oxevy.LOGGER.warn("[WebGui] account {} failed for '{}': {}", action, name, e.message)
            false
        }
        sendJson(out, 200, JsonObject().apply {
            addProperty("ok", ok)
            if (!ok) addProperty("error", "Could not $action account")
        })
    }

    private fun handleAccountsStatic(out: OutputStream, path: String) {
        val rawPath = path.removePrefix("/accounts/")
        if (rawPath.contains("..")) { sendText(out, 403, "Forbidden"); return }
        val resourcePath = if (rawPath.isBlank()) "webaccounts/index.html" else "webaccounts/$rawPath"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: run { sendText(out, 404, "Not found: $resourcePath"); return }
        val bytes = stream.use { s -> val buf = java.io.ByteArrayOutputStream(); s.copyTo(buf); buf.toByteArray() }
        val mime = contentType(resourcePath)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            appendCors()
            // no-store: CEF keeps a persistent profile and this UI changes
            // between builds. The shared immutable caching used for the other
            // web surfaces serves stale pages here, which shows up as a fetch
            // failing against markup that no longer matches the bundle.
            append("Cache-Control: no-store, must-revalidate\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun handleCosmeticsList(out: OutputStream) {
        val obj = com.google.gson.JsonObject()
        val arr = com.google.gson.JsonArray()
        try {
            val items = listCosmetics()
            items.forEach { arr.add(it) }
        } catch (e: Throwable) {
            Oxevy.LOGGER.warn("[WebGui] cosmetics list failed: {}", e.message)
        }
        obj.add("cosmetics", arr)
        obj.add("equipped", getEquipped())
        sendJson(out, 200, obj)
    }

    private fun listCosmetics(): List<com.google.gson.JsonObject> {
        val list = mutableListOf<com.google.gson.JsonObject>()
        try {
            list.add(com.google.gson.JsonObject().apply {
                addProperty("id", "angel_wings")
                addProperty("name", "Angel Wings")
            })
            list.add(com.google.gson.JsonObject().apply {
                addProperty("id", "devil_horns")
                addProperty("name", "Devil Horns")
            })
            list.add(com.google.gson.JsonObject().apply {
                addProperty("id", "crown")
                addProperty("name", "Crown")
            })
            list.add(com.google.gson.JsonObject().apply {
                addProperty("id", "headband")
                addProperty("name", "Headband")
            })
        } catch (e: Throwable) {}
        return list
    }

    private fun getEquipped(): com.google.gson.JsonArray {
        val arr = com.google.gson.JsonArray()
        me.alpha432.oxevy.manager.LocalCosmeticsManager.getEquipped().forEach { arr.add(it) }
        return arr
    }

    private fun handleCosmeticsEquip(out: OutputStream, body: String) {
        val json = parseJson(body) ?: com.google.gson.JsonObject()
        val id = json.get("id")?.asString ?: ""
        if (id.isBlank()) {
            sendJson(out, 400, com.google.gson.JsonObject().apply {
                addProperty("ok", false)
                addProperty("error", "missing id")
            })
            return
        }
        me.alpha432.oxevy.manager.LocalCosmeticsManager.equip(id)
        sendJson(out, 200, com.google.gson.JsonObject().apply {
            addProperty("ok", true)
            addProperty("equipped", id)
        })
    }

    private fun handleCosmeticsUnequip(out: OutputStream, body: String) {
        val json = parseJson(body) ?: com.google.gson.JsonObject()
        val id = json.get("id")?.asString ?: ""
        if (id.isBlank()) {
            sendJson(out, 400, com.google.gson.JsonObject().apply {
                addProperty("ok", false)
                addProperty("error", "missing id")
            })
            return
        }
        me.alpha432.oxevy.manager.LocalCosmeticsManager.unequip(id)
        sendJson(out, 200, com.google.gson.JsonObject().apply {
            addProperty("ok", true)
            addProperty("unequipped", id)
        })
    }

    private fun handleCosmeticsTheme(out: OutputStream, body: String) {
        val json = parseJson(body) ?: com.google.gson.JsonObject()
        val action = json.get("action")?.asString ?: ""
        when (action) {
            "save" -> {
                val name = json.get("name")?.asString ?: ""
                if (name.isBlank()) {
                    sendJson(out, 400, com.google.gson.JsonObject().apply {
                        addProperty("ok", false)
                        addProperty("error", "missing name")
                    })
                    return
                }
                val equipped = me.alpha432.oxevy.manager.LocalCosmeticsManager.getEquipped()
                me.alpha432.oxevy.manager.LocalCosmeticsManager.saveTheme(name, equipped)
                sendJson(out, 200, com.google.gson.JsonObject().apply { addProperty("ok", true) })
            }
            "load" -> {
                val name = json.get("name")?.asString ?: ""
                if (name.isBlank()) {
                    sendJson(out, 400, com.google.gson.JsonObject().apply {
                        addProperty("ok", false)
                        addProperty("error", "missing name")
                    })
                    return
                }
                val items = me.alpha432.oxevy.manager.LocalCosmeticsManager.loadTheme(name)
                items.forEach { me.alpha432.oxevy.manager.LocalCosmeticsManager.equip(it) }
                sendJson(out, 200, com.google.gson.JsonObject().apply { addProperty("ok", true) })
            }
            "list" -> {
                val themes = me.alpha432.oxevy.manager.LocalCosmeticsManager.listThemes()
                val obj = com.google.gson.JsonObject()
                val arr = com.google.gson.JsonArray()
                themes.forEach { arr.add(it) }
                obj.add("themes", arr)
                sendJson(out, 200, obj)
            }
            "delete" -> {
                val name = json.get("name")?.asString ?: ""
                if (name.isBlank()) {
                    sendJson(out, 400, com.google.gson.JsonObject().apply {
                        addProperty("ok", false)
                        addProperty("error", "missing name")
                    })
                    return
                }
                me.alpha432.oxevy.manager.LocalCosmeticsManager.deleteTheme(name)
                sendJson(out, 200, com.google.gson.JsonObject().apply { addProperty("ok", true) })
            }
            else -> sendJson(out, 400, com.google.gson.JsonObject().apply {
                addProperty("ok", false)
                addProperty("error", "unknown action")
            })
        }
    }

    private fun handleCosmeticsStatic(out: OutputStream, path: String) {
        val rawPath = path.removePrefix("/cosmetics/")
        if (rawPath.contains("..")) {
            sendText(out, 403, "Forbidden")
            return
        }
        val resourcePath = if (rawPath.isBlank()) "webcosmetics/index.html" else "webcosmetics/$rawPath"
        val clazz = this::class.java
        val stream = clazz.classLoader?.getResourceAsStream(resourcePath)
            ?: run {
                sendText(out, 404, "Not found: $resourcePath")
                return
            }
        val bytes = stream.use { s ->
            val buf = java.io.ByteArrayOutputStream()
            s.copyTo(buf)
            buf.toByteArray()
        }
        val mime = contentType(resourcePath)
        val header = StringBuilder().apply {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: ").append(mime).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            appendCors()
            append("Cache-Control: no-store, must-revalidate\r\n")
            append("Connection: close\r\n\r\n")
        }.toString()
        out.write(header.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun handleConfigStatic(out: OutputStream, path: String) {
        val rawPath = path.removePrefix("/config/")
        if (rawPath.contains("..")) { sendText(out, 403, "Forbidden"); return }
        val resourcePath = if (rawPath.isBlank()) "webconfig/index.html" else "webconfig/$rawPath"
        val clazz = this::class.java
        val stream = clazz.classLoader?.getResourceAsStream(resourcePath)
            ?: run { sendText(out, 404, "Not found: $resourcePath"); return }
        val bytes = stream.use { s ->
            val buf = java.io.ByteArrayOutputStream()
            s.copyTo(buf)
            buf.toByteArray()
        }
        val mime = contentType(resourcePath)
        val header = StringBuilder().apply {
            append("HTTP/1.1 200 OK\\r\\n")
            append("Content-Type: ").append(mime).append("\\r\\n")
            append("Content-Length: ").append(bytes.size).append("\\r\\n")
            appendCors()
            append("Cache-Control: no-store, must-revalidate\\r\\n")
            append("Connection: close\\r\\n\\r\\n")
        }.toString()
        out.write(header.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun handleConfigList(out: OutputStream) {
        val result = onClientThread {
            val arr = com.google.gson.JsonArray()
            me.alpha432.oxevy.Oxevy.configManager.listConfigs().forEach { arr.add(it) }
            com.google.gson.JsonObject().apply {
                addProperty("current", me.alpha432.oxevy.Oxevy.configManager.currentConfig)
                add("configs", arr)
            }
        }
        sendJson(out, 200, result)
    }

    private fun handleConfigCurrent(out: OutputStream) {
        val name = onClientThread { me.alpha432.oxevy.Oxevy.configManager.currentConfig }
        sendJson(out, 200, com.google.gson.JsonObject().apply { addProperty("current", name) })
    }

    private fun handleConfigSave(out: OutputStream, body: String) {
        val json = parseJson(body) ?: com.google.gson.JsonObject()
        val name = json.get("name")?.asString ?: ""
        val error = onClientThread {
            if (name.isBlank()) "missing name"
            else if (!me.alpha432.oxevy.manager.ConfigManager.isValidName(name)) "invalid name"
            else if (!me.alpha432.oxevy.Oxevy.configManager.saveProfile(name)) "save failed"
            else null
        }
        if (error != null) {
            sendJson(out, 400, com.google.gson.JsonObject().apply {
                addProperty("ok", false); addProperty("error", error)
            })
        } else {
            sendJson(out, 200, com.google.gson.JsonObject().apply { addProperty("ok", true) })
        }
    }

    private fun handleConfigLoad(out: OutputStream, body: String) {
        val json = parseJson(body) ?: com.google.gson.JsonObject()
        val name = json.get("name")?.asString ?: ""
        val error = onClientThread {
            if (name.isBlank()) "missing name"
            else if (!me.alpha432.oxevy.Oxevy.configManager.loadProfile(name)) "load failed"
            else null
        }
        if (error != null) {
            sendJson(out, 400, com.google.gson.JsonObject().apply {
                addProperty("ok", false); addProperty("error", error)
            })
        } else {
            sendJson(out, 200, com.google.gson.JsonObject().apply { addProperty("ok", true) })
        }
    }

    private fun handleConfigDelete(out: OutputStream, body: String) {
        val json = parseJson(body) ?: com.google.gson.JsonObject()
        val name = json.get("name")?.asString ?: ""
        val error = onClientThread {
            if (name.isBlank()) "missing name"
            else if (name == "default") "cannot delete default"
            else if (!me.alpha432.oxevy.Oxevy.configManager.deleteProfile(name)) "delete failed"
            else null
        }
        if (error != null) {
            sendJson(out, 400, com.google.gson.JsonObject().apply {
                addProperty("ok", false); addProperty("error", error)
            })
        } else {
            sendJson(out, 200, com.google.gson.JsonObject().apply { addProperty("ok", true) })
        }
    }

    /** POST {name} -> {ok, bundle} with the whole profile packed as one JSON object. */
    private fun handleConfigExport(out: OutputStream, body: String) {
        val json = parseJson(body) ?: com.google.gson.JsonObject()
        val name = json.get("name")?.asString ?: ""
        val bundle = onClientThread {
            if (name.isBlank()) null
            else me.alpha432.oxevy.Oxevy.configManager.exportProfile(name)
        }
        if (bundle == null) {
            sendJson(out, 404, com.google.gson.JsonObject().apply {
                addProperty("ok", false); addProperty("error", "config not found")
            })
        } else {
            sendJson(out, 200, com.google.gson.JsonObject().apply {
                addProperty("ok", true); add("bundle", bundle)
            })
        }
    }

    /** POST {name, bundle} -> writes an exported bundle back to disk. */
    private fun handleConfigImport(out: OutputStream, body: String) {
        val json = parseJson(body) ?: com.google.gson.JsonObject()
        val name = json.get("name")?.asString ?: ""
        val bundle = json.get("bundle")
        val error = onClientThread {
            if (name.isBlank()) "missing name"
            else if (!me.alpha432.oxevy.manager.ConfigManager.isValidName(name)) "invalid name"
            else if (bundle == null || !bundle.isJsonObject) "missing bundle"
            else if (!me.alpha432.oxevy.Oxevy.configManager.importProfile(name, bundle.asJsonObject)) "import failed"
            else null
        }
        if (error != null) {
            sendJson(out, 400, com.google.gson.JsonObject().apply {
                addProperty("ok", false); addProperty("error", error)
            })
        } else {
            sendJson(out, 200, com.google.gson.JsonObject().apply { addProperty("ok", true) })
        }
    }
}
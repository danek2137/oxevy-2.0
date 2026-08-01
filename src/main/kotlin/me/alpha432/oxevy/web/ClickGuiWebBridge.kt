package me.alpha432.oxevy.web

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.alpha432.oxevy.Oxevy
import me.alpha432.oxevy.features.modules.Module
import me.alpha432.oxevy.features.settings.Bind
import me.alpha432.oxevy.features.settings.Setting
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var activePort: Int = 8765

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
            socket.soTimeout = 5_000
            socket.use {
                val rawIn = it.getInputStream()
                val out   = it.getOutputStream()

                // Read the request line-by-line until we have the full headers
                val headerLines = mutableListOf<String>()
                val reader = BufferedReader(InputStreamReader(rawIn, StandardCharsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val fullPath = parts[1]
                val path = fullPath.substringBefore("?")

                var line = reader.readLine()
                while (!line.isNullOrBlank()) {
                    headerLines.add(line)
                    line = reader.readLine()
                }

                val headers = mutableMapOf<String, String>()
                for (hl in headerLines) {
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
                    req.method == "OPTIONS"                                   -> sendCors(out)
                    req.method == "GET" && req.path == "/api/state"           -> handleState(out)
                    req.method == "POST" && req.path == "/api/module/toggle"  -> handleToggle(out, req.body)
                    req.method == "POST" && req.path == "/api/setting"        -> handleSetting(out, req.body)
                    req.method == "GET" && req.path == "/api/capes/state"     -> handleCapeState(out)
                    req.method == "POST" && req.path == "/api/cape/upload"    -> handleCapeUploadRaw(out, req)
                    req.method == "POST" && req.path == "/api/cape/select"    -> handleCapeSelect(out, req.body)
                    req.method == "GET" && req.path == "/api/capes/repo"      -> handleCapeRepo(out)
                    req.method == "GET" && req.path.startsWith("/capes/")     -> handleCapeFile(out, req.path)
                    req.method == "GET"                                       -> handleStatic(out, req.path)
                    else -> sendText(out, 405, "Method Not Allowed")
                }
            }
        } catch (e: Exception) {
            Oxevy.LOGGER.debug("[WebGui] Client error: {}", e.message)
        }
    }

    // ── Route handlers ──────────────────────────────────────────────────────

    private fun handleState(out: OutputStream) {
        val state = onClientThread { buildState() }
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
        sendJson(out, 200, onClientThread { buildState() })
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
        sendJson(out, 200, onClientThread { buildState() })
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
        return root
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
        path.endsWith(".css")  -> "text/css; charset=utf-8"
        path.endsWith(".ttf")  -> "font/ttf"
        path.endsWith(".woff") -> "font/woff"
        path.endsWith(".woff2") -> "font/woff2"
        path.endsWith(".png")  -> "image/png"
        path.endsWith(".ico")  -> "image/x-icon"
        else                   -> "application/octet-stream"
    }

    private fun reasonPhrase(status: Int) = when (status) {
        200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"
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
}

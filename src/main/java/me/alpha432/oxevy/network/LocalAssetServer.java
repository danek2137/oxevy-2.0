package me.alpha432.oxevy.network;

import me.alpha432.oxevy.Oxevy;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal static-file HTTP server bound to 127.0.0.1:8765 serving the game's
 * <gameDir>/oxevy folder. The launcher writes capes/skins there and the mod
 * references them as http://127.0.0.1:8765/... URLs, which the game's normal
 * texture downloader can fetch no special texture registration needed.
 */
public class LocalAssetServer {
    public static final int PORT = 8765;

    private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("oxevy").toAbsolutePath().normalize();
    private static ServerSocket serverSocket;
    private static ExecutorService executor;
    private static volatile boolean running = false;

    private LocalAssetServer() {}

    public static synchronized void start() {
        if (running) return;
        running = true;
        executor = Executors.newCachedThreadPool();
        try {
            Files.createDirectories(ROOT);
            serverSocket = new ServerSocket(PORT, 50);
            executor.submit(LocalAssetServer::acceptLoop);
            Oxevy.LOGGER.info("[OxevyAssets] Serving {} on 127.0.0.1:{}", ROOT, PORT);
        } catch (IOException e) {
            Oxevy.LOGGER.error("[OxevyAssets] Failed to bind 127.0.0.1:{}", PORT, e);
        }
    }

    private static void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                executor.submit(() -> handle(client));
            } catch (IOException e) {
                if (running) {
                    Oxevy.LOGGER.error("[OxevyAssets] Accept error", e);
                }
                break;
            }
        }
    }

    private static void handle(Socket client) {
        try (Socket ignored = client) {
            client.setSoTimeout(5000);
            String request = readRequest(client);
            if (request == null) return;

            String[] lines = request.split("\r\n");
            if (lines.length == 0) return;
            String[] parts = lines[0].split(" ");
            if (parts.length < 2) return;

            if (!"GET".equals(parts[0])) {
                respond(client, 405, "Method Not Allowed", "text/plain", "405".getBytes(StandardCharsets.UTF_8));
                return;
            }

            String path = parts[1].split("\\?")[0];
            Path file = resolve(path);
            if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
                respond(client, 404, "Not Found", "text/plain", "404".getBytes(StandardCharsets.UTF_8));
                return;
            }

            byte[] data = Files.readAllBytes(file);
            respond(client, 200, "OK", contentType(path), data);
        } catch (Exception e) {
            Oxevy.LOGGER.error("[OxevyAssets] Error handling request", e);
        }
    }

    private static Path resolve(String urlPath) {
        String relative = urlPath.replace('\\', '/').replaceAll("^/+", "");
        if (relative.isEmpty()) return null;
        Path target = ROOT.resolve(relative).normalize();
        if (!target.startsWith(ROOT)) return null;
        return target;
    }

    private static String contentType(String path) {
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private static String readRequest(Socket client) throws IOException {
        InputStream in = client.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0;
        while (true) {
            int b = in.read();
            if (b == -1) break;
            buf.write(b);
            if (state == 0 && b == '\r') state = 1;
            else if (state == 1 && b == '\n') state = 2;
            else if (state == 2 && b == '\r') state = 3;
            else if (state == 3 && b == '\n') break;
            else state = (b == '\r') ? 1 : 0;
            if (buf.size() > 65536) break;
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static void respond(Socket client, int status, String reason, String contentType, byte[] body) throws IOException {
        OutputStream out = client.getOutputStream();
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        head.append("Content-Type: ").append(contentType).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }
}

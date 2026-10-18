package com.oxevy.safemod.music;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads and caches the album-art image referenced by the shared-memory
 * {@code artUrl} (either an https:// URL or a local file:// path). The HUD
 * renders the returned image as the track cover.
 */
public final class AlbumArtLoader {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private AlbumArtLoader() {
    }

    /** Returns a cached image for the URL, or null if none/none available yet. */
    public static BufferedImage get(String artUrl) {
        if (artUrl == null || artUrl.isBlank()) return null;
        if (CACHE.containsKey(artUrl)) return CACHE.get(artUrl);
        BufferedImage img = load(artUrl);
        if (img != null) {
            CACHE.put(artUrl, img);
        }
        return img;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static BufferedImage load(String artUrl) {
        try {
            byte[] bytes;
            if (artUrl.startsWith("file:")) {
                String pathPart = artUrl.substring(5);
                if (pathPart.startsWith("///")) pathPart = pathPart.substring(2);
                Path p = Path.of(URI.create("file:" + pathPart));
                if (!Files.exists(p)) return null;
                bytes = Files.readAllBytes(p);
            } else {
                HttpRequest req = HttpRequest.newBuilder(URI.create(artUrl))
                        .timeout(Duration.ofSeconds(6))
                        .header("User-Agent", "Mozilla/5.0 (oxevy)")
                        .GET()
                        .build();
                HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() != 200) return null;
                bytes = resp.body();
            }
            if (bytes == null || bytes.length == 0) return null;
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                return ImageIO.read(in);
            }
        } catch (Exception e) {
            return null;
        }
    }
}

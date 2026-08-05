package me.alpha432.oxevy.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ModDataManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModData");
    private static final String API_URL = "https://mod-api-point.daneq.workers.dev";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static class RemoteUser {
        public final String name;
        public final boolean online;

        public RemoteUser(String name, boolean online) {
            this.name = name;
            this.online = online;
        }
    }

    public void writeUsername(String username) {
        writeStatus(username, true);
    }

    public void writeStatus(String username, boolean online) {
        if (username == null || username.isEmpty()) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("online", online);
        payload.addProperty("hwid", me.alpha432.oxevy.util.HWID.get());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/write"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() == 200) {
                        LOGGER.info("[OxevyMod] Saved status {}: {}", online, username);
                    } else {
                        LOGGER.warn("[OxevyMod] Failed to write, status: {}", res.statusCode());
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("[OxevyMod] Error writing username", e);
                    return null;
                });
    }

    public void fetchCape(String nick, Consumer<String> onResult) {
        if (nick == null || nick.isEmpty()) return;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/cape/" + nick))
                .header("Accept", "application/json")
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() != 200) {
                        LOGGER.warn("[OxevyMod] Failed to fetch cape, status: {}", res.statusCode());
                        onResult.accept(null);
                        return;
                    }
                    try {
                        JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
                        if (obj.has("ok") && !obj.get("ok").getAsBoolean()) {
                            onResult.accept(null);
                            return;
                        }
                        JsonElement cosmetic = obj.get("cosmetic");
                        if (cosmetic == null || cosmetic.isJsonNull()) {
                            onResult.accept(null);
                            return;
                        }
                        JsonElement texture = cosmetic.getAsJsonObject().get("texture");
                        if (texture == null || texture.isJsonNull()) {
                            onResult.accept(null);
                            return;
                        }
                        onResult.accept(texture.getAsString());
                    } catch (Exception ex) {
                        LOGGER.warn("[OxevyMod] Failed to parse cape response", ex);
                        onResult.accept(null);
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("[OxevyMod] Error fetching cape", e);
                    onResult.accept(null);
                    return null;
                });
    }

    public void fetchSkin(String nick, Consumer<String> onResult) {
        if (nick == null || nick.isEmpty()) return;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/skin/" + nick))
                .header("Accept", "application/json")
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() != 200) {
                        LOGGER.warn("[OxevyMod] Failed to fetch skin, status: {}", res.statusCode());
                        onResult.accept(null);
                        return;
                    }
                    try {
                        JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
                        if (obj.has("ok") && !obj.get("ok").getAsBoolean()) {
                            onResult.accept(null);
                            return;
                        }
                        JsonElement skinUrl = obj.get("skinUrl");
                        if (skinUrl == null || skinUrl.isJsonNull()) {
                            onResult.accept(null);
                            return;
                        }
                        onResult.accept(skinUrl.getAsString());
                    } catch (Exception ex) {
                        LOGGER.warn("[OxevyMod] Failed to parse skin response", ex);
                        onResult.accept(null);
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("[OxevyMod] Error fetching skin", e);
                    onResult.accept(null);
                    return null;
                });
    }

    public String fetchCapeSync(String nick) {
        if (nick == null || nick.isEmpty()) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/cape/" + nick))
                    .timeout(Duration.ofMillis(3000))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                LOGGER.warn("[OxevyMod] Failed to fetch cape (sync), status: {}", res.statusCode());
                return null;
            }
            JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
            if (obj.has("ok") && !obj.get("ok").getAsBoolean()) return null;
            JsonElement cosmetic = obj.get("cosmetic");
            if (cosmetic == null || cosmetic.isJsonNull()) return null;
            JsonElement texture = cosmetic.getAsJsonObject().get("texture");
            return (texture == null || texture.isJsonNull()) ? null : texture.getAsString();
        } catch (Exception ex) {
            LOGGER.warn("[OxevyMod] Failed to fetch cape (sync)", ex);
            return null;
        }
    }

    public String fetchSkinSync(String nick) {
        if (nick == null || nick.isEmpty()) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/skin/" + nick))
                    .timeout(Duration.ofMillis(3000))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                LOGGER.warn("[OxevyMod] Failed to fetch skin (sync), status: {}", res.statusCode());
                return null;
            }
            JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
            if (obj.has("ok") && !obj.get("ok").getAsBoolean()) return null;
            JsonElement skinUrl = obj.get("skinUrl");
            return (skinUrl == null || skinUrl.isJsonNull()) ? null : skinUrl.getAsString();
        } catch (Exception ex) {
            LOGGER.warn("[OxevyMod] Failed to fetch skin (sync)", ex);
            return null;
        }
    }

    public void checkBan(String nick, String hwid, Consumer<Boolean> onResult) {
        String url = banCheckUrl(nick, hwid);
        if (url == null) {
            onResult.accept(false);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(5000))
                .header("Accept", "application/json")
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() != 200) {
                        LOGGER.warn("[OxevyMod] Failed to check ban, status: {}", res.statusCode());
                        onResult.accept(false);
                        return;
                    }
                    try {
                        JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
                        boolean banned = obj.has("banned") && obj.get("banned").getAsBoolean();
                        onResult.accept(banned);
                    } catch (Exception ex) {
                        LOGGER.warn("[OxevyMod] Failed to parse ban check", ex);
                        onResult.accept(false);
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("[OxevyMod] Error checking ban", e);
                    onResult.accept(false);
                    return null;
                });
    }

    public boolean checkBanSync(String nick, String hwid) {
        String url = banCheckUrl(nick, hwid);
        if (url == null) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(2500))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                LOGGER.warn("[OxevyMod] Failed to check ban (sync), status: {}", res.statusCode());
                return false;
            }
            JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
            return obj.has("banned") && obj.get("banned").getAsBoolean();
        } catch (Exception ex) {
            LOGGER.warn("[OxevyMod] Failed to check ban (sync)", ex);
            return false;
        }
    }

    private static String banCheckUrl(String nick, String hwid) {
        if ((nick == null || nick.isEmpty()) && (hwid == null || hwid.isEmpty())) return null;
        StringBuilder url = new StringBuilder(API_URL).append("/ban/check?");
        if (nick != null && !nick.isEmpty()) {
            url.append("nick=").append(java.net.URLEncoder.encode(nick, java.nio.charset.StandardCharsets.UTF_8));
            url.append('&');
        }
        url.append("hwid=").append(hwid == null ? "" : hwid);
        return url.toString();
    }

    public void reportHwid(String nick, String hwid) {
        if (nick == null || nick.isEmpty() || hwid == null || hwid.isEmpty()) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("nick", nick);
        payload.addProperty("hwid", hwid);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/ban/report"))
                .timeout(Duration.ofMillis(3000))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() != 200) {
                        LOGGER.warn("[OxevyMod] Failed to report hwid, status: {}", res.statusCode());
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("[OxevyMod] Error reporting hwid", e);
                    return null;
                });
    }

    public void readStatus(Consumer<List<RemoteUser>> onResult) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/read"))
                .timeout(Duration.ofMillis(5000))
                .header("Accept", "application/json")
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() != 200) {
                        LOGGER.warn("[OxevyMod] Failed to read, status: {}", res.statusCode());
                        return;
                    }
                    try {
                        List<RemoteUser> users = new ArrayList<>();
                        JsonArray array = JsonParser.parseString(res.body()).getAsJsonArray();
                        for (JsonElement e : array) {
                            if (e.isJsonObject()) {
                                JsonObject obj = e.getAsJsonObject();
                                JsonElement nameEl = obj.get("name");
                                if (nameEl == null || nameEl.isJsonNull()) continue;
                                boolean online = !obj.has("online") || obj.get("online").getAsBoolean();
                                users.add(new RemoteUser(nameEl.getAsString(), online));
                            } else if (e.isJsonPrimitive()) {
                                users.add(new RemoteUser(e.getAsString(), true));
                            }
                        }
                        onResult.accept(users);
                    } catch (Exception ex) {
                        LOGGER.warn("[OxevyMod] Failed to parse usernames", ex);
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("[OxevyMod] Error reading usernames", e);
                    return null;
                });
    }
}

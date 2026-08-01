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
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("[OxevyMod] Error fetching cape", e);
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
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("[OxevyMod] Error fetching skin", e);
                    return null;
                });
    }

    public void readStatus(Consumer<List<RemoteUser>> onResult) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/read"))
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

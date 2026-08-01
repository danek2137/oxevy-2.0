package me.alpha432.oxevy.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.Feature;
import me.alpha432.oxevy.util.traits.Jsonable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class OxevyUsersManager extends Feature implements Jsonable {
    private static final int REFRESH_INTERVAL_TICKS = 20 * 60 * 5;

    private final List<String> users = new ArrayList<>();
    private volatile List<String> remoteUsers = new ArrayList<>();
    private final ModDataManager dataManager = new ModDataManager();

    private ClientLevel lastLevel;
    private int refreshTicks = 0;
    private String lastKnownName = null;

    public OxevyUsersManager() {
        super("OxevyUsers");
    }

    public void init() {
        Oxevy.configManager.addConfig(this);
    }

    public void onTick() {
        if (mc.level == null || mc.player == null) {
            lastLevel = null;
            refreshTicks = 0;
            return;
        }

        if (lastLevel != mc.level) {
            lastLevel = mc.level;
            refreshTicks = 0;
            sync();
            return;
        }

        if (++refreshTicks >= REFRESH_INTERVAL_TICKS) {
            refreshTicks = 0;
            sync();
        }
    }

    private void sync() {
        String name = mc.player.getGameProfile().name();
        if (name != null && !name.isEmpty()) {
            lastKnownName = name;
        }
        dataManager.writeUsername(name);
        dataManager.readStatus(remote -> {
            List<String> online = new ArrayList<>();
            for (ModDataManager.RemoteUser user : remote) {
                if (user.online) {
                    online.add(user.name);
                }
            }
            remoteUsers = online;
        });
    }

    // Marks the last known player as offline on the backend when leaving a server.
    public void markOffline() {
        if (lastKnownName == null || lastKnownName.isEmpty()) return;
        dataManager.writeStatus(lastKnownName, false);
    }

    public boolean isKnown(String name) {
        if (this.users.stream().anyMatch(user -> user.equalsIgnoreCase(name))) return true;
        for (String remote : remoteUsers) {
            if (remote.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public boolean isKnown(Player player) {
        return this.isKnown(player.getGameProfile().name());
    }

    public void add(String name) {
        this.users.add(name);
    }

    public void remove(String name) {
        this.users.removeIf(s -> s.equalsIgnoreCase(name));
    }

    public List<String> getUsers() {
        return this.users;
    }

    public List<String> getRemoteUsers() {
        return this.remoteUsers;
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        for (String user : users) {
            array.add(user);
        }
        object.add("users", array);
        return object;
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject() || !element.getAsJsonObject().has("users")) {
            return;
        }
        for (JsonElement e : element.getAsJsonObject().get("users").getAsJsonArray()) {
            users.add(e.getAsString());
        }
    }

    @Override
    public String getFileName() {
        return "oxevy_users.json";
    }
}

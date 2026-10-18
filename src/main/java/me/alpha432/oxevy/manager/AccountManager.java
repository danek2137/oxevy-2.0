package me.alpha432.oxevy.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.mixin.accessor.MinecraftAccessor;
import me.alpha432.oxevy.util.traits.Jsonable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccountManager implements Jsonable {
    private final List<String> accounts = new ArrayList<>();

    public void init() {
        Oxevy.configManager.addConfig(this);
    }

    public void addAccount(String username) {
        if (username == null || username.trim().isEmpty()) return;
        String name = username.trim();
        if (!accounts.contains(name)) {
            accounts.add(name);
            Oxevy.configManager.save();
        }
    }

    public void removeAccount(String username) {
        if (username == null) return;
        accounts.removeIf(s -> s.equalsIgnoreCase(username));
        Oxevy.configManager.save();
    }

    public List<String> getAccounts() {
        return this.accounts;
    }

    public void switchAccount(String username) {
        try {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            User user = new User(
                username,
                uuid,
                "token",
                Optional.empty(),
                Optional.empty()
            );
            ((MinecraftAccessor) Minecraft.getInstance()).setUser(user);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        for (String acc : accounts) {
            array.add(acc);
        }
        object.add("accounts", array);
        return object;
    }

    @Override
    public void fromJson(JsonElement element) {
        accounts.clear();
        if (element != null && element.isJsonObject() && element.getAsJsonObject().has("accounts")) {
            for (JsonElement e : element.getAsJsonObject().get("accounts").getAsJsonArray()) {
                accounts.add(e.getAsString());
            }
        }
    }

    @Override
    public String getFileName() {
        return "accounts.json";
    }
}

package me.alpha432.oxevy.network;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.util.HWID;
import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class OxevyUserManager {
    private static final Set<UUID> oxevyUsers = new HashSet<>();

    public static void addOxevyUser(UUID uuid) {
        oxevyUsers.add(uuid);
    }

    public static void removeOxevyUser(UUID uuid) {
        oxevyUsers.remove(uuid);
    }

    public static boolean isOxevyUser(net.minecraft.world.entity.player.Player player) {
        return player != null && oxevyUsers.contains(player.getUUID());
    }

    public static boolean isOxevyUser(UUID uuid) {
        return oxevyUsers.contains(uuid);
    }

    public static void clear() {
        oxevyUsers.clear();
    }

    public static void sendHandshake() {
        OxevyPayload.sendHandshake();
    }

    public static void onPlayerDisconnect(UUID uuid) {
        removeOxevyUser(uuid);
    }

    public static void onWorldJoin() {
        String nick = null;
        try {
            String profileNick = Minecraft.getInstance().getGameProfile().name();
            if (profileNick != null && !profileNick.isEmpty()) nick = profileNick;
        } catch (Exception ignored) {
        }
        String hwid = HWID.get();
        boolean banned = Oxevy.modDataManager.checkBanSync(nick, hwid);
        Oxevy.LOGGER.info("World join ban check: nick={} banned={} (flag={})", nick, banned, Oxevy.banned);
        if (banned || Oxevy.banned) {
            if (banned) {
                Oxevy.modDataManager.reportHwid(nick, hwid);
                Oxevy.applyBan();
            }
            Command.sendMessage("{red}This copy of Oxevy is banned on this device (HWID).");
            return;
        }
        clear();
        me.alpha432.oxevy.manager.SelfCosmeticsManager.onWorldJoin();
        sendHandshake();
    }
}

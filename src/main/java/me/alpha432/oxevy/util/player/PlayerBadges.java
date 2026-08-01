package me.alpha432.oxevy.util.player;

import net.minecraft.world.entity.player.Player;

public class PlayerBadges {
    public static final String OWNER_NAME = "like_bakmc";
    public static final String TESTER_NAME = "kubix_2468";

    public static final String OWNER_ICON = "\uE000";
    public static final String TESTER_ICON = "\uE001";

    public static boolean isOwner(String name) {
        return name != null && name.equalsIgnoreCase(OWNER_NAME);
    }

    public static boolean isTester(String name) {
        return name != null && name.equalsIgnoreCase(TESTER_NAME);
    }

    public static boolean hasBadge(String name) {
        return isOwner(name) || isTester(name);
    }

    public static String getBadge(String name) {
        if (isOwner(name)) return OWNER_ICON;
        if (isTester(name)) return TESTER_ICON;
        return "";
    }

    public static String getBadge(Player player) {
        if (player == null) return "";
        return getBadge(player.getGameProfile().name());
    }

    public static String getBadgedName(String name) {
        String badge = getBadge(name);
        return badge.isEmpty() ? name : badge + " " + name;
    }
}

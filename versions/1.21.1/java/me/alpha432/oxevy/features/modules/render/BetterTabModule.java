package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;

public class BetterTabModule extends Module {

    public Setting<Integer> tabSize = num("TabSize", 100, 1, 1000);
    public Setting<Integer> tabHeight = num("ColumnHeight", 20, 1, 1000);
    public Setting<Boolean> highlightSelf = bool("HighlightSelf", true);
    public Setting<Color> selfColor = color("SelfColor", 250, 130, 30, 255);
    public Setting<Boolean> highlightFriends = bool("HighlightFriends", true);
    public Setting<Boolean> accurateLatency = bool("AccurateLatency", true);
    public Setting<Boolean> showGamemode = bool("ShowGamemode", false);

    public BetterTabModule() {
        super("BetterTab", "Various improvements to the tab list", Category.RENDER);
    }

    public Component getPlayerName(PlayerInfo entry) {
        Component name = entry.getTabListDisplayName();
        if (name == null) {
            name = Component.literal(entry.getProfile().getName());
        }

        Color color = null;

        if (entry.getProfile().getId().equals(mc.player.getUUID()) && highlightSelf.getValue()) {
            color = selfColor.getValue();
        } else if (highlightFriends.getValue() && mc.level != null) {
            for (Player p : mc.level.players()) {
                if (p.getUUID().equals(entry.getProfile().getId()) && Oxevy.friendManager.isFriend(p)) {
                    color = new Color(0, 255, 255);
                    break;
                }
            }
        }

        if (color != null) {
            String nameStr = name.getString();
            nameStr = nameStr.replaceAll("\u00a7.", "");
            final Color c = color;
            name = Component.literal(nameStr).withStyle(style -> style.withColor(c.getRGB() & 0xFFFFFF));
        }

        if (showGamemode.getValue()) {
            var gm = entry.getGameMode();
            String gmText = "?";
            if (gm != null) {
                gmText = switch (gm.getId()) {
                    case 0 -> "S";
                    case 1 -> "C";
                    case 2 -> "A";
                    case 3 -> "Sp";
                    default -> "?";
                };
            }
            MutableComponent text = Component.literal("");
            text.append(name);
            text.append(" [" + gmText + "]");
            name = text;
        }

        return name;
    }
}

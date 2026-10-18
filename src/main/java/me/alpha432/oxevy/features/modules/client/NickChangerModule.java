package me.alpha432.oxevy.features.modules.client;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class NickChangerModule extends Module {
    public final Setting<String> nick = str("Nick", "Oxevy");
    public final Setting<Boolean> chat = bool("Chat", true);
    public final Setting<Boolean> tab = bool("Tab", true);
    public final Setting<Boolean> nametags = bool("Nametags", true);

    public NickChangerModule() {
        super("NickChanger", "Locally hides your real nickname behind a custom one", Category.CLIENT);
    }

    public String realName() {
        try {
            String name = Minecraft.getInstance().getGameProfile().name();
            return name == null ? "" : name;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public String visibleNick() {
        String value = nick.getValue() == null ? "" : nick.getValue().trim();
        return value.isEmpty() ? realName() : value;
    }

    public boolean shouldReplace() {
        String real = realName();
        String replacement = visibleNick();
        return isEnabled() && !real.isBlank() && !replacement.isBlank() && !real.equals(replacement);
    }

    public String replace(String text) {
        if (!shouldReplace() || text == null || text.isEmpty()) return text;
        return text.replace(realName(), visibleNick());
    }

    public Component replace(Component component) {
        if (!shouldReplace() || component == null) return component;
        String text = component.getString();
        String replaced = replace(text);
        if (text.equals(replaced)) return component;
        return Component.literal(replaced).withStyle(component.getStyle());
    }

    @Override
    public String getDisplayInfo() {
        return visibleNick();
    }
}

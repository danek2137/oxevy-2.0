package me.alpha432.oxevy.features.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.ClientEvent;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.features.Feature;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.settings.Bind;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.manager.ConfigManager;
import me.alpha432.oxevy.sound.OxevySounds;
import me.alpha432.oxevy.util.ColorUtil;
import me.alpha432.oxevy.util.traits.Jsonable;
import me.alpha432.oxevy.util.traits.Toggleable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.joml.Vector2f;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;

public class Module extends Feature implements Jsonable, Toggleable {
    private final String description;
    private Category category;

    public final Setting<Boolean> enabled = bool("Enabled", false);
    public final Setting<Boolean> drawn = bool("Drawn", true);
    public final Setting<Bind> bind = key("Keybind", new Bind(GLFW_KEY_UNKNOWN));
    public final Setting<String> displayName;

    public boolean hidden;

    public Module(String name, String description, Category category) {
        super(name);
        this.displayName = str("DisplayName", name);
        this.description = description;
        this.category = category;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onToggle() {
    }

    public void onLoad() {
    }

    public void onTick() {
    }

    public void onRender2D(Render2DEvent event) {
    }

    public void onRender3D(Render3DEvent event) {
    }

    public void onUnload() {
    }

    public String getDisplayInfo() {
        return null;
    }

    public void enable() {
        if (Oxevy.tempDisabled && !Oxevy.tempDisabledModules.contains(getName())) return;
        if (Oxevy.destroyed) return;
        if (Oxevy.banned) return;
        this.enabled.setValue(true);
        EVENT_BUS.register(this);
        EVENT_BUS.post(new ClientEvent(ClientEvent.Type.TOGGLE_MODULE, this));
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(OxevySounds.ENABLE, 1.0f));
        }
        this.onToggle();
        if (nullCheck()) return;
        this.onEnable();
    }

    public void disable() {
        this.enabled.setValue(false);
        EVENT_BUS.unregister(this);
        EVENT_BUS.post(new ClientEvent(ClientEvent.Type.TOGGLE_MODULE, this));
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(OxevySounds.DISABLE, 1.0f));
        }
        this.onToggle();
        if (nullCheck()) return;
        this.onDisable();
    }

    public String getDisplayName() {
        return this.displayName.getValue();
    }

    public void setDisplayName(String name) {
        Module module = Oxevy.moduleManager.getModuleByDisplayName(name);
        Module originalModule = Oxevy.moduleManager.getModuleByName(name);
        if (module == null && originalModule == null) {
            Command.sendMessage(this.getDisplayName() + ", name: " + this.getName() + ", has been renamed to: " + name);
            this.displayName.setValue(name);
            return;
        }
        Command.sendMessage("{red} A module of this name already exists.");
    }

    @Override
    public boolean isEnabled() {
        return enabled.getValue();
    }

    @Override
    public boolean isToggled() {
        return isEnabled();
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isDrawn() {
        return this.drawn.getValue();
    }

    public void setDrawn(boolean drawn) {
        this.drawn.setValue(drawn);
    }

    public Category getCategory() {
        return this.category;
    }

    public String getInfo() {
        return null;
    }

    public Bind getBind() {
        return this.bind.getValue();
    }

    public void setBind(int key) {
        this.bind.setValue(new Bind(key));
    }

    public String getFullArrayString() {
        return this.getDisplayName() + ChatFormatting.GRAY + (this.getDisplayInfo() != null ? " [" + ChatFormatting.WHITE + this.getDisplayInfo() + ChatFormatting.GRAY + "]" : "");
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("Category", category.name());
        for (Setting<?> setting : getSettings()) {
            try {
                if (setting.getValue() instanceof Bind keyBind) {
                    object.addProperty(setting.getName(), keyBind.getKey());
                } else if (setting.getValue() instanceof java.awt.Color color) {
                    object.addProperty(setting.getName(), color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha());
                } else if (setting.getValue() instanceof Vector2f pos) {
                    object.addProperty(setting.getName(), pos.x() + "," + pos.y());
                } else {
                    object.addProperty(setting.getName(), setting.getValueAsString());
                }
            } catch (Throwable e) {
                Oxevy.LOGGER.error("Failed to create JSON field", e);
            }
        }
        return object;
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        JsonObject object = element.getAsJsonObject();
        // Load category if present
        if (object.has("Category")) {
            try {
                String categoryName = object.get("Category").getAsString();
                for (Category cat : Category.values()) {
                    if (cat.name().equalsIgnoreCase(categoryName)) {
                        this.category = cat;
                        break;
                    }
                }
            } catch (Exception e) {
                Oxevy.LOGGER.error("Failed to load category from JSON", e);
            }
        }
        if (object.has("Enabled")) {
            boolean shouldBeEnabled = Boolean.parseBoolean(object.get("Enabled").getAsString());
            if (shouldBeEnabled && !isEnabled()) {
                enable();
            } else if (!shouldBeEnabled && isEnabled()) {
                disable();
            }
        }
        for (Setting<?> setting : getSettings()) {
            try {
                JsonElement settingElement = object.get(setting.getName());
                if (settingElement != null && !settingElement.isJsonNull()) {
                    ConfigManager.setValueFromJson(this, setting, settingElement);
                }
            } catch (Throwable throwable) {
                Oxevy.LOGGER.error("Failed to load from JSON", throwable);
            }
        }
    }

    public enum Category {
        COMBAT("Combat", "C", "CO"),
        MISC("Misc", "s", "MI"),
        RENDER("Render", "r", "RE"),
        MOVEMENT("Movement", "v", "MO"),
        PLAYER("Player", "p", "PL"),
        CLIENT("Client", "t", "CL"),
        HUD("Hud", "H", "HD");

        private final String name;
        private final String icon;
        private final String shortName;

        Category(String name, String icon, String shortName) {
            this.name = name;
            this.icon = icon;
            this.shortName = shortName;
        }

        public String getName() {
            return this.name;
        }

        public String getIcon() {
            return this.icon;
        }

        public String getShortName() {
            return this.shortName;
        }
    }
}

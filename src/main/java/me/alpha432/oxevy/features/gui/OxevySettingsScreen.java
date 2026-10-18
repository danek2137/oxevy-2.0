package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Bind;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.AnimationUtil;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OxevySettingsScreen extends Screen {
    private static final Minecraft mc = Minecraft.getInstance();

    private final Map<Module.Category, List<Module>> modulesByCategory = new EnumMap<>(Module.Category.class);
    private final Map<Setting<?>, SettingComponent> settingComponents = new HashMap<>();

    private Module.Category selectedCategory = Module.Category.COMBAT;
    private Module selectedModule = null;

    private float openAnimation = 0f;
    private int settingsScrollOffset = 0;
    private int maxSettingsScroll = 0;
    private int moduleScrollOffset = 0;
    private int maxModuleScroll = 0;

    private static final int WIDTH = 520;
    private static final int HEIGHT = 380;
    private static final int SIDEBAR_WIDTH = 90;
    private static final int MODULE_WIDTH = 130;

    private static final String HUD_EDITOR_ICON = "⚙";
    private static final String CONFIG_ICON = "💾";
    private static final int ICON_BTN_SIZE = 14;
    private static final int ICON_BTN_PAD = 6;

    private Setting<String> focusedStringSetting = null;
    private Setting<Bind> listeningBindSetting = null;

    public OxevySettingsScreen() {
        super(Component.literal("Oxevy Settings"));

        for (Module.Category cat : Module.Category.values()) {
            modulesByCategory.put(cat, new ArrayList<>());
        }
        for (Module module : Oxevy.moduleManager.getModules()) {
            if (!module.hidden) {
                modulesByCategory.get(module.getCategory()).add(module);
            }
        }
    }

    private String getCategoryIcon(Module.Category category) {
        return switch (category) {
            case COMBAT -> "⚔";
            case MISC -> "⚙";
            case RENDER -> "👁";
            case MOVEMENT -> "✈";
            case PLAYER -> "👤";
            case CLIENT -> "🛠";
            case HUD -> "📊";
            default -> "📁";
        };
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        openAnimation = AnimationUtil.animate(openAnimation, 1.0f, 0.15f, AnimationUtil.Easing.EASE_OUT);

        int x = (context.guiWidth() - WIDTH) / 2;
        int y = (context.guiHeight() - HEIGHT) / 2;

        // Draw background shadow
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), new Color(0, 0, 0, (int) (150 * openAnimation)).getRGB());

        // Main window background
        RenderUtil.rect(context, x, y, x + WIDTH, y + HEIGHT, 0xFF121212);
        RenderUtil.rect(context, x, y, x + WIDTH, y + 2, ClickGuiModule.getInstance().color.getValue().getRGB());

        // Config save button (top-right, left of HUD button)
        int cfgBtnX = x + WIDTH - 2 * ICON_BTN_PAD - 2 * ICON_BTN_SIZE;
        int cfgBtnY = y + ICON_BTN_PAD;
        boolean cfgBtnHover = mouseX >= cfgBtnX && mouseX <= cfgBtnX + ICON_BTN_SIZE && mouseY >= cfgBtnY && mouseY <= cfgBtnY + ICON_BTN_SIZE;
        int cfgBtnBg = cfgBtnHover ? 0x33FFFFFF : 0x22000000;
        RenderUtil.rect(context, cfgBtnX, cfgBtnY, cfgBtnX + ICON_BTN_SIZE, cfgBtnY + ICON_BTN_SIZE, cfgBtnBg);
        RenderUtil.rect(context, cfgBtnX, cfgBtnY, cfgBtnX + ICON_BTN_SIZE, cfgBtnY + ICON_BTN_SIZE, 0x33FFFFFF, 1.0f);
        int cfgIconColor = cfgBtnHover ? 0xFFFFFFFF : 0xFFBBBBBB;
        context.drawString(CustomFont.font(), CONFIG_ICON, cfgBtnX + 4, cfgBtnY + 3, cfgIconColor);

        // HUD editor quick button (top-right)
        int btnX = x + WIDTH - ICON_BTN_PAD - ICON_BTN_SIZE;
        int btnY = y + ICON_BTN_PAD;
        boolean btnHover = mouseX >= btnX && mouseX <= btnX + ICON_BTN_SIZE && mouseY >= btnY && mouseY <= btnY + ICON_BTN_SIZE;
        int btnBg = btnHover ? 0x33FFFFFF : 0x22000000;
        RenderUtil.rect(context, btnX, btnY, btnX + ICON_BTN_SIZE, btnY + ICON_BTN_SIZE, btnBg);
        RenderUtil.rect(context, btnX, btnY, btnX + ICON_BTN_SIZE, btnY + ICON_BTN_SIZE, 0x33FFFFFF, 1.0f);
        int iconColor = btnHover ? 0xFFFFFFFF : 0xFFBBBBBB;
        context.drawString(CustomFont.font(), HUD_EDITOR_ICON, btnX + 4, btnY + 3, iconColor);

        // Sidebar (Categories)
        RenderUtil.rect(context, x, y + 2, x + SIDEBAR_WIDTH, y + HEIGHT, 0xFF181818);
        int categoryY = y + 15;
        for (Module.Category cat : Module.Category.values()) {
            boolean selected = cat == selectedCategory;
            boolean hovered = mouseX >= x && mouseX <= x + SIDEBAR_WIDTH && mouseY >= categoryY - 2 && mouseY <= categoryY + 12;
            int textColor = selected ? ClickGuiModule.getInstance().color.getValue().getRGB() : (hovered ? 0xFFCCCCCC : 0xFF888888);

            if (selected) {
                RenderUtil.rect(context, x, categoryY - 2, x + 2, categoryY + 12, ClickGuiModule.getInstance().color.getValue().getRGB());
            }

            String icon = getCategoryIcon(cat);
            context.drawString(CustomFont.font(), icon, x + 8, categoryY, textColor);
            context.drawString(CustomFont.font(), cat.getName(), x + 25, categoryY, textColor);
            categoryY += 20;
        }

        // Module list
        int moduleX = x + SIDEBAR_WIDTH;
        RenderUtil.rect(context, moduleX, y + 2, moduleX + MODULE_WIDTH, y + HEIGHT, 0xFF151515);

        List<Module> mods = modulesByCategory.get(selectedCategory);
        maxModuleScroll = 0;
        int moduleY = y + 15 - moduleScrollOffset;
        if (mods != null) {
            for (Module mod : mods) {
                if (moduleY < y - 5 || moduleY > y + HEIGHT - 20) {
                    moduleY += 18;
                    continue;
                }
                boolean selected = mod == selectedModule;
                boolean enabled = mod.isEnabled();
                int textColor = selected ? 0xFFFFFFFF : (enabled ? 0xFFBBBBBB : 0xFF666666);

                if (selected) {
                    RenderUtil.rect(context, moduleX + 5, moduleY - 2, moduleX + MODULE_WIDTH - 5, moduleY + 12, 0x22FFFFFF);
                }
                if (enabled) {
                    int c = ClickGuiModule.getInstance().color.getValue().getRGB();
                    RenderUtil.rect(context, moduleX + 5, moduleY - 2, moduleX + 7, moduleY + 12, c);
                }

                context.drawString(CustomFont.font(), mod.getDisplayName(), moduleX + 10, moduleY, textColor);
                moduleY += 18;
            }
            maxModuleScroll = Math.max(0, moduleY - (y + HEIGHT) + 20);
        }

        // Settings area
        int settingsX = moduleX + MODULE_WIDTH;
        int settingsAreaHeight = HEIGHT - 30;
        if (selectedModule != null) {
            context.drawString(CustomFont.font(), selectedModule.getDisplayName() + " Settings", settingsX + 15, y + 15, 0xFFAAAAAA);

            int settingY = y + 32 - settingsScrollOffset;
            maxSettingsScroll = 0;
            for (Setting<?> setting : selectedModule.getSettings()) {
                if (setting.getName().equals("Enabled") || setting.getName().equals("DisplayName")) continue;
                if (!setting.isVisible()) continue;

                SettingComponent comp = settingComponents.computeIfAbsent(setting, s -> createComponent(s, settingsX + 15, 0));
                comp.x = settingsX + 15;
                comp.y = settingY;
                comp.render(context, mouseX, mouseY, delta);
                settingY += comp.getHeight() + 4;
            }
            maxSettingsScroll = Math.max(0, settingY - (y + settingsAreaHeight) - 35);
        } else {
            String text = "Select a module to view settings";
            context.drawString(CustomFont.font(), text,
                settingsX + (WIDTH - SIDEBAR_WIDTH - MODULE_WIDTH - CustomFont.font().width(text)) / 2,
                y + HEIGHT / 2,
                0xFF444444
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Stop any active drags when clicking anywhere (sliders, etc.)
        if (button == 0) {
            for (SettingComponent comp : settingComponents.values()) {
                comp.mouseReleased(mouseX, mouseY, button);
            }
        }
        for (SettingComponent comp : settingComponents.values()) {
            if (comp instanceof BindComponent bc) bc.stopListening();
        }
        listeningBindSetting = null;

        int x = (mc.getWindow().getGuiScaledWidth() - WIDTH) / 2;
        int y = (mc.getWindow().getGuiScaledHeight() - HEIGHT) / 2;

        // Config save button
        int cfgBtnX = x + WIDTH - ICON_BTN_PAD * 3 - ICON_BTN_SIZE * 2;
        int cfgBtnY = y + ICON_BTN_PAD;
        if (button == 0 && mouseX >= cfgBtnX && mouseX <= cfgBtnX + ICON_BTN_SIZE && mouseY >= cfgBtnY && mouseY <= cfgBtnY + ICON_BTN_SIZE) {
            mc.setScreen(new ConfigsScreen());
            return true;
        }

        // HUD editor quick button
        int btnX = x + WIDTH - ICON_BTN_PAD - ICON_BTN_SIZE;
        int btnY = y + ICON_BTN_PAD;
        if (button == 0 && mouseX >= btnX && mouseX <= btnX + ICON_BTN_SIZE && mouseY >= btnY && mouseY <= btnY + ICON_BTN_SIZE) {
            mc.setScreen(HudEditorScreen.getInstance());
            return true;
        }

        // Category clicks
        int categoryY = y + 15;
        for (Module.Category cat : Module.Category.values()) {
            if (mouseX >= x && mouseX <= x + SIDEBAR_WIDTH && mouseY >= categoryY - 2 && mouseY <= categoryY + 12) {
                selectedCategory = cat;
                selectedModule = null;
                settingComponents.clear();
                moduleScrollOffset = 0;
                return true;
            }
            categoryY += 20;
        }

        // Module clicks
        int moduleX = x + SIDEBAR_WIDTH;
        int moduleY = y + 15 - moduleScrollOffset;
        List<Module> mods = modulesByCategory.get(selectedCategory);
        if (mods != null) {
            for (Module mod : mods) {
                if (moduleY < y - 5 || moduleY > y + HEIGHT - 20) {
                    moduleY += 18;
                    continue;
                }
                if (mouseX >= moduleX && mouseX <= moduleX + MODULE_WIDTH && mouseY >= moduleY - 2 && mouseY <= moduleY + 12) {
                    if (button == 0) {
                        selectedModule = mod;
                        settingComponents.clear();
                        settingsScrollOffset = 0;
                    } else if (button == 1) {
                        mod.toggle();
                    }
                    return true;
                }
                moduleY += 18;
            }
        }

        // Setting clicks iterate in render order, skip invisible settings
        if (selectedModule != null) {
            for (Setting<?> setting : selectedModule.getSettings()) {
                if (setting.getName().equals("Enabled") || setting.getName().equals("DisplayName")) continue;
                if (!setting.isVisible()) continue;

                SettingComponent comp = settingComponents.get(setting);
                if (comp == null) continue;

                if (comp.isHovered(mouseX, mouseY)) {
                    comp.mouseClicked(mouseX, mouseY, button);
                    if (comp instanceof StringComponent && ((StringComponent) comp).isFocused()) {
                        focusedStringSetting = (Setting<String>) comp.getSetting();
                    }
                    if (comp instanceof BindComponent bc && bc.isListening()) {
                        listeningBindSetting = (Setting<Bind>) comp.getSetting();
                        focusedStringSetting = null;
                    }
                    return true;
                }
            }
        }

        focusedStringSetting = null;
        listeningBindSetting = null;

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        for (SettingComponent comp : settingComponents.values()) {
            comp.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int x = (mc.getWindow().getGuiScaledWidth() - WIDTH) / 2;
        int y = (mc.getWindow().getGuiScaledHeight() - HEIGHT) / 2;
        int settingsX = x + SIDEBAR_WIDTH + MODULE_WIDTH;
        int settingsAreaHeight = HEIGHT - 30;

        if (mouseX >= settingsX && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + HEIGHT) {
            settingsScrollOffset = Math.max(0, Math.min(maxSettingsScroll, settingsScrollOffset - (int) (verticalAmount * 10)));
            return true;
        }
        int moduleAreaX = x + SIDEBAR_WIDTH;
        if (mouseX >= moduleAreaX && mouseX <= moduleAreaX + MODULE_WIDTH && mouseY >= y && mouseY <= y + HEIGHT) {
            moduleScrollOffset = Math.max(0, Math.min(maxModuleScroll, moduleScrollOffset - (int) (verticalAmount * 10)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();

        if (listeningBindSetting != null) {
            listeningBindSetting.setValue(key == GLFW.GLFW_KEY_ESCAPE ? Bind.none() : new Bind(key));
            listeningBindSetting = null;
            for (SettingComponent comp : settingComponents.values()) {
                if (comp instanceof BindComponent bc) bc.stopListening();
            }
            return true;
        }

        if (focusedStringSetting != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                focusedStringSetting = null;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                String s = focusedStringSetting.getValue();
                if (!s.isEmpty()) focusedStringSetting.setValue(s.substring(0, s.length() - 1));
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (focusedStringSetting != null) {
            String chr = input.codepointAsString();
            if (!chr.isEmpty()) {
                String s = focusedStringSetting.getValue();
                if (s.length() < 64) focusedStringSetting.setValue(s + chr);
            }
            return true;
        }
        return super.charTyped(input);
    }

    private SettingComponent createComponent(Setting setting, int x, int y) {
        Object value = setting.getValue();
        int compWidth = WIDTH - SIDEBAR_WIDTH - MODULE_WIDTH - 30;

        if (value instanceof Boolean) return new BooleanComponent((Setting<Boolean>) setting, x, y, compWidth, 12);
        if (value instanceof Number && setting.getMin() != null) return new SliderComponent((Setting<Number>) setting, x, y, compWidth, 12);
        if (value instanceof Bind) return new BindComponent((Setting<Bind>) setting, x, y, compWidth, 12);
        if (setting.isEnumSetting()) return new EnumComponent(setting, x, y, compWidth, 12);
        if (setting.isStringSetting()) return new StringComponent((Setting<String>) setting, x, y, compWidth, 12);
        if (setting.isButtonSetting()) return new ButtonComponent((Setting<Runnable>) setting, x, y, compWidth, 12);
        if (value instanceof Color) return new ColorComponent((Setting<Color>) setting, x, y, compWidth, 12);
        return new StringSettingComponent(setting, x, y, compWidth, 12);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open() {
        mc.setScreen(new OxevySettingsScreen());
    }
}


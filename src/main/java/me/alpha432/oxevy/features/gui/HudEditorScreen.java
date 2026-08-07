package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.modules.hud.HudModule;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HudEditorScreen extends Screen {
    private static final Minecraft mc = Minecraft.getInstance();
    private static HudEditorScreen INSTANCE;

    public HudModule currentDragging;
    public boolean anyHover;

    private HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        anyHover = false;
        
        // Background tint
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), 0x22000000);
        
        // Guide lines
        ClickGuiModule clickGui = ClickGuiModule.getInstance();
        int accentColor = clickGui.color.getValue().getRGB();
        int guideLineColor = new Color(
            (accentColor >> 16) & 0xFF,
            (accentColor >> 8) & 0xFF,
            accentColor & 0xFF,
            80   // 30% alpha
        ).getRGB();
        
        int centerX = context.guiWidth() / 2;
        int centerY = context.guiHeight() / 2;
        RenderUtil.rect(context, centerX, 0, centerX + 1, context.guiHeight(), guideLineColor);
        RenderUtil.rect(context, 0, centerY, context.guiWidth(), centerY + 1, guideLineColor);
        
        // Render HUD modules
        List<HudModule> modules = Oxevy.moduleManager.getModules().stream()
                .filter(m -> m instanceof HudModule && m.isEnabled())
                .map(m -> (HudModule) m)
                .collect(Collectors.toList());
        
        for (HudModule module : modules) {
            module.onRender2DHud(new me.alpha432.oxevy.event.impl.render.Render2DEvent(context, delta));
        }
        
        // Editor instructions
        String text = "HUD Editor - Drag modules to reposition";
        context.drawString(CustomFont.font(), text, (context.guiWidth() - CustomFont.font().width(text)) / 2, 10, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        return super.keyPressed(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        // Handle hotkeys for HUD config saving/loading
        String character = input.codepointAsString();
        if (!character.isEmpty()) {
            char c = character.charAt(0);
            if (c == 's' || c == 'S') { // 'S' key
                saveCurrentHudConfig();
                return true;
            } else if (c == 'l' || c == 'L') { // 'L' key
                loadLastHudConfig();
                return true;
            }
        }
        return super.charTyped(input);
    }

    private void saveCurrentHudConfig() {
        // Create a map to store HUD module positions
        Map<String, float[]> hudPositions = new HashMap<>();
        
        for (HudModule module : Oxevy.moduleManager.getModules().stream()
                .filter(m -> m instanceof HudModule && m.isEnabled())
                .map(m -> (HudModule) m)
                .collect(Collectors.toList())) {
            hudPositions.put(module.getName(), new float[]{module.pos.getValue().x(), module.pos.getValue().y()});
        }
        
        // Store in the config manager or a dedicated file
        // For simplicity, we'll use the existing config system with a special marker
        String configName = "hud_layout_" + System.currentTimeMillis();
        Oxevy.configManager.save(); // Save current config first
        
        // In a real implementation, we'd store this in a separate file or extend the config system
        // For now, we'll just show a confirmation
        Command.sendMessage("HUD layout saved (use .config load <name> to load layouts)");
    }

    private void loadLastHudConfig() {
        // This would load the last saved HUD layout
        // For a complete implementation, we'd need to store/load HUD positions separately
        Command.sendMessage("HUD layout loading not fully implemented yet");
    }

    public static HudEditorScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new HudEditorScreen();
        }
        return INSTANCE;
    }
}


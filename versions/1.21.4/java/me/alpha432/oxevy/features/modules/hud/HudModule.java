package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.input.MouseInputEvent;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.gui.HudEditorScreen;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.joml.Vector2f;

public abstract class HudModule extends Module {
    public final Setting<Vector2f> pos = vec2f("Position", 0.1f, 0.1f);
    public final Setting<Boolean> blur = bool("Blur", false);
    public final Setting<Boolean> rounded = bool("Rounded", true);
    public final Setting<Float> radius = num("Radius", 4.0f, 0.0f, 10.0f);

    // Pose settings
    public final Setting<Boolean> modifyPose = bool("Modify Pose", false);
    public final Setting<Float> scale = num("Scale", 1.0f, 0.1f, 5.0f);
    public final Setting<Float> rotation = num("Rotation", 0.0f, -360.0f, 360.0f);
    public final Setting<Runnable> resetPose = button("Reset Pose", () -> {
        scale.setValue(1.0f);
        rotation.setValue(0.0f);
    });

    private float dragX, dragY, width, height;
    private boolean dragging, button;
    
    // Animation for hover and general state
    protected float hoverAnim = 0.0f;

    public HudModule(String name, String description, float width, float height) {
        super(name, description, Category.HUD);
        this.width = width;
        this.height = height;

        radius.setVisibility(v -> rounded.getValue());
        scale.setVisibility(v -> modifyPose.getValue());
        rotation.setVisibility(v -> modifyPose.getValue());
        resetPose.setVisibility(v -> modifyPose.getValue());
    }

    public float getX() {
        return mc.getWindow().getGuiScaledWidth() * pos.getValue().x();
    }

    public float getY() {
        float heightWithChat = mc.getWindow().getGuiScaledHeight() - 14;
        float baseY = mc.getWindow().getGuiScaledHeight() * pos.getValue().y();
        float combined = baseY + getHeight();

        if (mc.screen instanceof ChatScreen) {
            baseY = Math.min(combined, heightWithChat) - getHeight();
        }
        return baseY;
    }

    public abstract void drawContent(Render2DEvent e);

    @Subscribe
    public void onRender2DHud(Render2DEvent e) {
        if (nullCheck()) return;

        float x = getX();
        float y = getY();
        
        updateAnimations();

        if (mc.screen instanceof HudEditorScreen && button) {
            if (!dragging && isHovering() && HudEditorScreen.getInstance().currentDragging == null) {
                dragX = getMouseX() - x;
                dragY = getMouseY() - y;
                dragging = true;
                HudEditorScreen.getInstance().currentDragging = this;
            }

            if (dragging) {
                float finalX = Math.min(Math.max(getMouseX() - dragX, 0),
                        mc.getWindow().getGuiScaledWidth() - width);
                float finalY = Math.min(Math.max(getMouseY() - dragY, 0),
                        mc.getWindow().getGuiScaledHeight() - height);

                pos.getValue().x = finalX / mc.getWindow().getGuiScaledWidth();
                pos.getValue().y = finalY / mc.getWindow().getGuiScaledHeight();
            }
        } else {
            dragging = false;
        }

        GuiGraphics context = e.getContext();
        
        context.pose().pushPose();
        if (modifyPose.getValue()) {
            context.pose().translate(x + width / 2f, y + height / 2f,0.0F);
            context.pose().scale(scale.getValue(), scale.getValue(), 1.0F);
            context.pose().translate(-(x + width / 2f), -(y + height / 2f), 0.0F);
        }

        if (mc.screen instanceof HudEditorScreen) {
            boolean isThisDragging = HudEditorScreen.getInstance().currentDragging == this;
            int borderColor = isThisDragging ? ClickGuiModule.getInstance().color.getValue().getRGB() : 0x55FFFFFF;
            
            RenderUtil.rect(context, x - 2, y - 2, x + width + 2, y + height + 2, borderColor, 1.0f);
            
            if (isHovering() || isThisDragging) {
                String label = getName();
                context.drawString(mc.font, label, (int) x, (int) (y - 12), 0xFFFFFFFF);
            }
        }

        drawContent(e);
        
        context.pose().popPose();
    }
    
    protected void updateAnimations() {
        float target = isHovering() ? 1.0f : 0.0f;
        hoverAnim = me.alpha432.oxevy.util.render.AnimationUtil.animate(hoverAnim, target, 0.1f);
    }
    
    @Subscribe
    public void onMouse(MouseInputEvent e) {
        if (!(mc.screen instanceof HudEditorScreen) || nullCheck()) return;

        if (e.getAction() == 0) {
            button = false;
            dragging = false;
            HudEditorScreen.getInstance().currentDragging = null;
        }

        if (e.getAction() == 1 && isHovering()) {
            button = true;
        }
    }

    public int getMouseX() {
        return (int) (mc.mouseHandler.xpos() / mc.getWindow().getGuiScale());
    }

    public int getMouseY() {
        return (int) (mc.mouseHandler.ypos() / mc.getWindow().getGuiScale());
    }

    public boolean isHovering() {
        float x = getX();
        float y = getY();
        int mouseX = getMouseX();
        int mouseY = getMouseY();

        return mouseX >= x - 2 && mouseX <= x + width + 2 &&
                mouseY >= y - 2 && mouseY <= y + height + 2;
    }

    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public void setWidth(float width) { this.width = width; }
    public void setHeight(float height) { this.height = height; }
}
package me.alpha432.oxevy.features.gui;

import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.CustomFont;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;

import java.awt.Color;

public class DropdownComponent extends SettingComponent {
    private static final Minecraft mc = Minecraft.getInstance();
    private final Enum<?>[] values;
    private final int itemHeight = 14;
    private boolean open = false;

    public DropdownComponent(Setting setting, int x, int y, int width, int height) {
        super(setting, x, y, width, height);
        Object value = setting.getValue();
        this.values = value instanceof Enum<?> ? (Enum<?>[]) value.getClass().getEnumConstants() : new Enum<?>[0];
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Color accent = ClickGuiModule.getInstance().color.getValue();
        boolean headerHover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        RenderUtil.rect(context, x, y, x + width, y + height, headerHover ? 0xFF1E1E30 : 0xFF1A1A28);

        int textY = y + (height - 8) / 2;
        context.drawString(CustomFont.font(), setting.getName(), x + 6, textY, 0xFFE6E6E6);

        String val = setting.currentEnumName();
        int valColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 230).getRGB();
        context.drawString(CustomFont.font(), val, x + width - 20 - CustomFont.font().width(val), textY, valColor);
        context.drawString(CustomFont.font(), open ? "v" : ">", x + width - 10, textY, open ? valColor : 0xFF9E9EAA);

        if (!open) return;

        RenderUtil.rect(context, x, y + height, x + width, y + height + values.length * itemHeight, 0xFF141422);
        RenderUtil.rect(context, x + 4, y + height, x + width - 4, y + height + 1, 0xFF2A2A3E);

        int iy = y + height;
        for (Enum<?> value : values) {
            boolean rowHover = mouseX >= x && mouseX <= x + width && mouseY >= iy && mouseY < iy + itemHeight;
            boolean selected = value == setting.getValue();
            if (rowHover) {
                RenderUtil.rect(context, x, iy, x + width, iy + itemHeight, 0xFF1E1E30);
            }
            if (selected) {
                RenderUtil.rect(context, x, iy, x + 3, iy + itemHeight, accent.getRGB());
            }
            int color = selected ? accent.getRGB() : (rowHover ? 0xFFFFFFFF : 0xFF9E9EAA);
            context.drawString(CustomFont.font(), niceName(value), x + 8, iy + (itemHeight - 8) / 2, color);
            iy += itemHeight;
        }
    }

    @Override
    public boolean isCapturingInput() {
        return open;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (open && input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            open = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (open) {
            int iy = y + height;
            for (Enum<?> value : values) {
                if (mouseX >= x && mouseX <= x + width && mouseY >= iy && mouseY < iy + itemHeight) {
                    setValue(value);
                    open = false;
                    return true;
                }
                iy += itemHeight;
            }
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                open = false;
                return true;
            }
        } else if (isHovered(mouseX, mouseY)) {
            open = true;
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void setValue(Enum<?> value) {
        setting.setValue(value);
    }

    @Override
    public int getHeight() {
        return open ? height + values.length * itemHeight : height;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    private static String niceName(Enum<?> value) {
        String name = value.name().toLowerCase().replace('_', ' ');
        if (name.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(name.length());
        boolean capitalize = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == ' ') {
                sb.append(' ');
                capitalize = true;
            } else {
                sb.append(capitalize ? Character.toUpperCase(c) : c);
                capitalize = false;
            }
        }
        return sb.toString();
    }
}

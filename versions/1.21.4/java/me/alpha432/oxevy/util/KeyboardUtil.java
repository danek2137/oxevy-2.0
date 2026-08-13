package me.alpha432.oxevy.util;

import me.alpha432.oxevy.features.settings.Bind;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class KeyboardUtil {
    public static String getKeyName(int key) {
        String str = new Bind(key).toString().toUpperCase();
        str = str.replace("KEY.KEYBOARD", "").replace(".", " ").trim();
        return str;
    }

    public static String getTypedCharacter(int key, boolean shiftDown) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char value = (char) ('a' + (key - GLFW.GLFW_KEY_A));
            return String.valueOf(shiftDown ? Character.toUpperCase(value) : value);
        }

        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            if (!shiftDown) {
                return String.valueOf((char) ('0' + (key - GLFW.GLFW_KEY_0)));
            }

            return switch (key) {
                case GLFW.GLFW_KEY_1 -> "!";
                case GLFW.GLFW_KEY_2 -> "@";
                case GLFW.GLFW_KEY_3 -> "#";
                case GLFW.GLFW_KEY_4 -> "$";
                case GLFW.GLFW_KEY_5 -> "%";
                case GLFW.GLFW_KEY_6 -> "^";
                case GLFW.GLFW_KEY_7 -> "&";
                case GLFW.GLFW_KEY_8 -> "*";
                case GLFW.GLFW_KEY_9 -> "(";
                case GLFW.GLFW_KEY_0 -> ")";
                default -> null;
            };
        }

        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> " ";
            case GLFW.GLFW_KEY_APOSTROPHE -> shiftDown ? "\"" : "'";
            case GLFW.GLFW_KEY_COMMA -> shiftDown ? "<" : ",";
            case GLFW.GLFW_KEY_MINUS -> shiftDown ? "_" : "-";
            case GLFW.GLFW_KEY_PERIOD -> shiftDown ? ">" : ".";
            case GLFW.GLFW_KEY_SLASH -> shiftDown ? "?" : "/";
            case GLFW.GLFW_KEY_SEMICOLON -> shiftDown ? ":" : ";";
            case GLFW.GLFW_KEY_EQUAL -> shiftDown ? "+" : "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> shiftDown ? "{" : "[";
            case GLFW.GLFW_KEY_BACKSLASH -> shiftDown ? "|" : "\\";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> shiftDown ? "}" : "]";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> shiftDown ? "~" : "`";
            default -> {
                String name = GLFW.glfwGetKeyName(key, 0);
                if (name == null || name.isBlank()) {
                    yield null;
                }

                yield shiftDown ? name.toUpperCase() : name;
            }
        };
    }

    public static boolean isShiftDown() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    public static boolean isControlDown() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}

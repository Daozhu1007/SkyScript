package dev.skyscript.input;

import org.lwjgl.glfw.GLFW;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 键名表：名字 ↔ GLFW 键码 ↔ AWT VK 码（OS 级模拟用）。
 * 键名是配置文件和 JSON 脚本里人类可读的按键写法，如 A / D / W / SPACE / PGDN / F8。
 */
public final class KeyNames {

    private static final Map<String, Integer> NAME_TO_GLFW = new LinkedHashMap<>();
    private static final Map<Integer, String> GLFW_TO_NAME = new HashMap<>();
    private static final Map<Integer, Integer> GLFW_TO_VK = new HashMap<>();

    private KeyNames() {
    }

    private static void reg(int glfw, String name, Integer vk) {
        NAME_TO_GLFW.put(name, glfw);
        GLFW_TO_NAME.put(glfw, name);
        if (vk != null) GLFW_TO_VK.put(glfw, vk);
    }

    static {
        for (int i = 0; i < 26; i++) reg(GLFW.GLFW_KEY_A + i, String.valueOf((char) ('A' + i)), KeyEvent.VK_A + i);
        for (int i = 0; i < 10; i++) reg(GLFW.GLFW_KEY_0 + i, String.valueOf((char) ('0' + i)), KeyEvent.VK_0 + i);
        for (int i = 0; i < 24; i++) reg(GLFW.GLFW_KEY_F1 + i, "F" + (i + 1), KeyEvent.VK_F1 + i);
        for (int i = 0; i < 10; i++) reg(GLFW.GLFW_KEY_KP_0 + i, "KP" + i, KeyEvent.VK_NUMPAD0 + i);

        reg(GLFW.GLFW_KEY_SPACE, "SPACE", KeyEvent.VK_SPACE);
        reg(GLFW.GLFW_KEY_ENTER, "ENTER", KeyEvent.VK_ENTER);
        reg(GLFW.GLFW_KEY_ESCAPE, "ESC", KeyEvent.VK_ESCAPE);
        reg(GLFW.GLFW_KEY_TAB, "TAB", KeyEvent.VK_TAB);
        reg(GLFW.GLFW_KEY_LEFT_SHIFT, "LSHIFT", KeyEvent.VK_SHIFT);
        reg(GLFW.GLFW_KEY_RIGHT_SHIFT, "RSHIFT", KeyEvent.VK_SHIFT);
        reg(GLFW.GLFW_KEY_LEFT_CONTROL, "LCTRL", KeyEvent.VK_CONTROL);
        reg(GLFW.GLFW_KEY_RIGHT_CONTROL, "RCTRL", KeyEvent.VK_CONTROL);
        reg(GLFW.GLFW_KEY_LEFT_ALT, "LALT", KeyEvent.VK_ALT);
        reg(GLFW.GLFW_KEY_RIGHT_ALT, "RALT", KeyEvent.VK_ALT);
        reg(GLFW.GLFW_KEY_LEFT, "LEFT", KeyEvent.VK_LEFT);
        reg(GLFW.GLFW_KEY_RIGHT, "RIGHT", KeyEvent.VK_RIGHT);
        reg(GLFW.GLFW_KEY_UP, "UP", KeyEvent.VK_UP);
        reg(GLFW.GLFW_KEY_DOWN, "DOWN", KeyEvent.VK_DOWN);
        reg(GLFW.GLFW_KEY_HOME, "HOME", KeyEvent.VK_HOME);
        reg(GLFW.GLFW_KEY_END, "END", KeyEvent.VK_END);
        reg(GLFW.GLFW_KEY_PAGE_UP, "PGUP", KeyEvent.VK_PAGE_UP);
        reg(GLFW.GLFW_KEY_PAGE_DOWN, "PGDN", KeyEvent.VK_PAGE_DOWN);
        reg(GLFW.GLFW_KEY_INSERT, "INSERT", KeyEvent.VK_INSERT);
        reg(GLFW.GLFW_KEY_DELETE, "DELETE", KeyEvent.VK_DELETE);
        reg(GLFW.GLFW_KEY_BACKSPACE, "BACKSPACE", KeyEvent.VK_BACK_SPACE);
        reg(GLFW.GLFW_KEY_CAPS_LOCK, "CAPS", KeyEvent.VK_CAPS_LOCK);
        reg(GLFW.GLFW_KEY_MINUS, "MINUS", KeyEvent.VK_MINUS);
        reg(GLFW.GLFW_KEY_EQUAL, "EQUALS", KeyEvent.VK_EQUALS);
        reg(GLFW.GLFW_KEY_LEFT_BRACKET, "LBRACKET", KeyEvent.VK_OPEN_BRACKET);
        reg(GLFW.GLFW_KEY_RIGHT_BRACKET, "RBRACKET", KeyEvent.VK_CLOSE_BRACKET);
        reg(GLFW.GLFW_KEY_SEMICOLON, "SEMICOLON", KeyEvent.VK_SEMICOLON);
        reg(GLFW.GLFW_KEY_APOSTROPHE, "APOSTROPHE", KeyEvent.VK_QUOTE);
        reg(GLFW.GLFW_KEY_GRAVE_ACCENT, "GRAVE", KeyEvent.VK_BACK_QUOTE);
        reg(GLFW.GLFW_KEY_BACKSLASH, "BACKSLASH", KeyEvent.VK_BACK_SLASH);
        reg(GLFW.GLFW_KEY_COMMA, "COMMA", KeyEvent.VK_COMMA);
        reg(GLFW.GLFW_KEY_PERIOD, "PERIOD", KeyEvent.VK_PERIOD);
        reg(GLFW.GLFW_KEY_SLASH, "SLASH", KeyEvent.VK_SLASH);
    }

    /** 名字 → GLFW 键码；不认识返回 null */
    public static Integer glfwOf(String name) {
        if (name == null) return null;
        return NAME_TO_GLFW.get(name.trim().toUpperCase());
    }

    /** GLFW 键码 → 名字 */
    public static String nameOf(int glfw) {
        String n = GLFW_TO_NAME.get(glfw);
        return n != null ? n : ("KEY_" + glfw);
    }

    /** GLFW 键码 → AWT VK 码（OS 级模拟用）；不支持返回 null */
    public static Integer vkOf(int glfw) {
        return GLFW_TO_VK.get(glfw);
    }

    /** 移动类按键：由移动注入处理 */
    public static boolean isMovementKey(int glfw) {
        return glfw == GLFW.GLFW_KEY_A || glfw == GLFW.GLFW_KEY_D
                || glfw == GLFW.GLFW_KEY_W || glfw == GLFW.GLFW_KEY_S
                || glfw == GLFW.GLFW_KEY_SPACE || glfw == GLFW.GLFW_KEY_LEFT_SHIFT;
    }

    /** 横向移动键（A/D），脚本的"方向"概念 */
    public static boolean isLateralKey(String name) {
        return "A".equals(name) || "D".equals(name);
    }
}

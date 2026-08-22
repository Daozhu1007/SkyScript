package dev.skyscript.input;

import dev.skyscript.mixin.KeyboardAccessor;
import dev.skyscript.mixin.MouseAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/**
 * 游戏内事件注入：把按键/鼠标事件直接送进 Minecraft 的事件管线
 * （经 mixin @Invoker 调用 26.2 的私有 keyPress / onButton）。
 * 对走 MC 键位系统的功能（vanilla 键位、Fabric mod 键位、SkyHanni 等）等效于真实按键。
 * 局限：GLFW 层注册的第三方监听（如 Lunar 内置功能）收不到，需要 OS 级模拟兜底。
 */
public final class KeySimulator {

    private KeySimulator() {
    }

    private static Minecraft client() {
        return Minecraft.getInstance();
    }

    /** 有界面打开时不注入（避免把按键打进聊天框/输入框） */
    public static boolean safe() {
        return client() != null && client().gui.screen() == null;
    }

    public static void tapKey(int glfw) {
        Minecraft c = client();
        if (c == null || c.gui.screen() != null) return;
        long w = c.getWindow().handle();
        KeyboardAccessor ka = (KeyboardAccessor) c.keyboardHandler;
        KeyEvents.setInjecting(true);
        try {
            ka.skyScript$keyPress(w, GLFW.GLFW_PRESS, new KeyEvent(glfw, 0, 0));
            ka.skyScript$keyPress(w, GLFW.GLFW_RELEASE, new KeyEvent(glfw, 0, 0));
        } finally {
            KeyEvents.setInjecting(false);
        }
    }

    public static void tapMouseLeft() {
        Minecraft c = client();
        if (c == null || c.gui.screen() != null) return;
        long w = c.getWindow().handle();
        MouseAccessor ma = (MouseAccessor) c.mouseHandler;
        KeyEvents.setInjecting(true);
        try {
            ma.skyScript$onButton(w, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0), GLFW.GLFW_PRESS);
            ma.skyScript$onButton(w, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0), GLFW.GLFW_RELEASE);
        } finally {
            KeyEvents.setInjecting(false);
        }
    }
}

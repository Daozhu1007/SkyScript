package dev.skyscript.input;

import dev.skyscript.mixin.KeyboardAccessor;
import dev.skyscript.mixin.MouseAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;

/**
 * 游戏内事件注入：把按键/鼠标事件直接送进 Minecraft 的事件管线
 * （经 mixin @Invoker 调用 1.21.11 的私有 onKey / onMouseButton）。
 * 对走 MC 键位系统的功能（vanilla 键位、Fabric mod 键位、SkyHanni 等）等效于真实按键。
 * 局限：GLFW 层注册的第三方监听（如 Lunar 内置功能）收不到，需要 OS 级模拟兜底。
 */
public final class KeySimulator {

    private KeySimulator() {
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    /** 有界面打开时不注入（避免把按键打进聊天框/输入框） */
    public static boolean safe() {
        return client() != null && client().currentScreen == null;
    }

    public static void tapKey(int glfw) {
        MinecraftClient c = client();
        if (c == null || c.currentScreen != null) return;
        long w = c.getWindow().getHandle();
        KeyboardAccessor ka = (KeyboardAccessor) c.keyboard;
        ka.skyScript$onKey(w, GLFW.GLFW_PRESS, new KeyInput(glfw, 0, 0));
        ka.skyScript$onKey(w, GLFW.GLFW_RELEASE, new KeyInput(glfw, 0, 0));
    }

    public static void tapMouseLeft() {
        MinecraftClient c = client();
        if (c == null || c.currentScreen != null) return;
        long w = c.getWindow().getHandle();
        MouseAccessor ma = (MouseAccessor) c.mouse;
        ma.skyScript$onMouseButton(w, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0), GLFW.GLFW_PRESS);
        ma.skyScript$onMouseButton(w, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0), GLFW.GLFW_RELEASE);
    }
}

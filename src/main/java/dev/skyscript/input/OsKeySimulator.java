package dev.skyscript.input;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;

/**
 * OS 级按键模拟（java.awt.Robot）。
 * 用于收不到游戏内事件注入的目标（如 Lunar 内置功能的快捷键、窗口外的按键）。
 * 局限：需要游戏窗口有焦点；不支持 VK 映射的键会被跳过。
 */
public final class OsKeySimulator {

    private static Robot robot;
    private static boolean tried;

    private OsKeySimulator() {
    }

    public static synchronized Robot robot() {
        if (!tried) {
            tried = true;
            try {
                robot = new Robot();
                robot.setAutoDelay(10);
            } catch (AWTException e) {
                robot = null;
            }
        }
        return robot;
    }

    public static boolean available() {
        return robot() != null;
    }

    public static boolean tap(int glfw) {
        Integer vk = KeyNames.vkOf(glfw);
        if (vk == null) return false;
        Robot r = robot();
        if (r == null) return false;
        r.keyPress(vk);
        r.keyRelease(vk);
        return true;
    }

    public static boolean press(int glfw) {
        Integer vk = KeyNames.vkOf(glfw);
        if (vk == null) return false;
        Robot r = robot();
        if (r == null) return false;
        r.keyPress(vk);
        return true;
    }

    public static boolean release(int glfw) {
        Integer vk = KeyNames.vkOf(glfw);
        if (vk == null) return false;
        Robot r = robot();
        if (r == null) return false;
        r.keyRelease(vk);
        return true;
    }
}

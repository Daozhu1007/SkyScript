package dev.skyscript.input;

import java.util.HashMap;
import java.util.Map;

/**
 * 按键抬起事件队列（由 KeyEventCatcher mixin 从 Keyboard.onKey 事件驱动填充）。
 * 事件驱动解决了轮询沿检测在快速点击时漏检的问题，语义与 AHK 的 key-up 触发一致。
 */
public final class KeyEvents {

    /** 防抖窗口：抬起事件至少 250ms 后才放行，避免同一次点击重复触发 */
    private static final long DEBOUNCE_MS = 250;
    private static final Map<Integer, Long> keyUpTimes = new HashMap<>();

    private KeyEvents() {
    }

    /** 由 mixin 调用：记录一次真实的按键抬起（GLFW keycode） */
    public static void onKeyUp(int keyCode) {
        keyUpTimes.put(keyCode, System.currentTimeMillis());
    }

    /**
     * 查询并消费指定键的抬起事件。
     * 事件在防抖窗口内返回 false（事件保留，供下次查询）；
     * 窗口过后返回 true 并清除事件。
     */
    public static boolean consumeKeyUp(int keyCode) {
        Long t = keyUpTimes.get(keyCode);
        if (t == null) return false;
        long now = System.currentTimeMillis();
        if (now - t < DEBOUNCE_MS) return false;
        keyUpTimes.remove(keyCode);
        return true;
    }

    /** 清理过老的事件，防止内存增长 */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        keyUpTimes.entrySet().removeIf(e -> now - e.getValue() > 5000);
    }

    public static void clear() {
        keyUpTimes.clear();
    }
}

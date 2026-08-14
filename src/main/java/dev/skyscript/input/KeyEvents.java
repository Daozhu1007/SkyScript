package dev.skyscript.input;

import java.util.HashMap;
import java.util.Map;

/**
 * 按键事件队列（由 KeyEventCatcher mixin 从 Keyboard.onKey 事件驱动填充）。
 *
 * <p>记录按下时间与松开时间，触发键仅在"短按（点击）松开"时生效：
 * 长按（按住超过 maxHoldMs）用于正常走路/操作，不会触发脚本，
 * 与"长按走路时脚本不该自己启动"的用户预期一致。
 */
public final class KeyEvents {

    /** 点击判定上限：按住时长超过该值视为长按，松开不触发 */
    public static final long MAX_CLICK_MS = 300;

    /** 防抖窗口：松开事件至少 250ms 后才放行，避免同一次点击重复触发 */
    private static final long DEBOUNCE_MS = 250;

    private static final Map<Integer, Long> keyDownTimes = new HashMap<>();
    private static final Map<Integer, Long> keyUpTimes = new HashMap<>();
    private static final Map<Integer, Long> keyUpDurations = new HashMap<>();

    private KeyEvents() {
    }

    /** 由 mixin 调用：记录按下（GLFW keycode） */
    public static void onKeyDown(int keyCode) {
        keyDownTimes.put(keyCode, System.currentTimeMillis());
    }

    /** 由 mixin 调用：记录松开（GLFW keycode）与按住时长 */
    public static void onKeyUp(int keyCode) {
        long now = System.currentTimeMillis();
        Long down = keyDownTimes.remove(keyCode);
        long duration = down == null ? 0 : now - down;
        keyUpTimes.put(keyCode, now);
        keyUpDurations.put(keyCode, duration);
    }

    /**
     * 查询并消费指定键的"点击"事件。
     * 仅在按住时长 ≤ maxHoldMs（点击）且已过防抖窗口时返回 true；
     * 长按的松开事件被丢弃，不会触发。
     */
    public static boolean consumeClick(int keyCode, long maxHoldMs) {
        Long t = keyUpTimes.get(keyCode);
        if (t == null) return false;
        long now = System.currentTimeMillis();
        if (now - t < DEBOUNCE_MS) return false;
        long duration = keyUpDurations.getOrDefault(keyCode, 0L);
        keyUpTimes.remove(keyCode);
        keyUpDurations.remove(keyCode);
        return duration <= maxHoldMs;
    }

    /** 清理过老的事件，防止内存增长 */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        keyUpTimes.entrySet().removeIf(e -> now - e.getValue() > 5000);
        keyUpDurations.entrySet().removeIf(e -> !keyUpTimes.containsKey(e.getKey()));
        keyDownTimes.entrySet().removeIf(e -> now - e.getValue() > 5000);
    }

    public static void clear() {
        keyDownTimes.clear();
        keyUpTimes.clear();
        keyUpDurations.clear();
    }
}

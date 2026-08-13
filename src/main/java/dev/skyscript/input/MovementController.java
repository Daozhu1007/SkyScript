package dev.skyscript.input;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 移动注入控制器：引擎每 tick 把"当前想按住的移动键"喂进来，
 * KeyboardInputMixin 在 KeyboardInput#tick 返回后覆写 playerInput / movementVector。
 * 空闲时 desired 为空 → 完全不影响 vanilla 移动。
 */
public final class MovementController {

    private static final Set<String> desired = new HashSet<>();

    private MovementController() {
    }

    public static void setDesired(Collection<String> keys) {
        desired.clear();
        if (keys != null) desired.addAll(keys);
    }

    public static void clear() {
        desired.clear();
    }

    public static boolean active() {
        return !desired.isEmpty();
    }

    /** 横向：A=-1, D=+1 */
    public static float getSideways() {
        float v = 0;
        if (desired.contains("A")) v -= 1;
        if (desired.contains("D")) v += 1;
        return v;
    }

    /** 纵向：W=+1, S=-1 */
    public static float getForward() {
        float v = 0;
        if (desired.contains("W")) v += 1;
        if (desired.contains("S")) v -= 1;
        return v;
    }

    public static boolean isJumping() {
        return desired.contains("SPACE");
    }

    public static boolean isSneaking() {
        return desired.contains("LSHIFT");
    }
}

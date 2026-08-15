package dev.skyscript.input;

/**
 * 真实鼠标按键状态（由 MouseEventCatcher mixin 更新），供 HUD 拖动等使用。
 */
public final class MouseButtons {

    private static boolean leftDown;

    private MouseButtons() {
    }

    public static void setLeft(boolean down) {
        leftDown = down;
    }

    public static boolean isLeftDown() {
        return leftDown;
    }

    public static void reset() {
        leftDown = false;
    }
}

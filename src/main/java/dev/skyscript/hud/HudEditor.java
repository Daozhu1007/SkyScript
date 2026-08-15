package dev.skyscript.hud;

import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.input.MouseButtons;
import net.minecraft.client.MinecraftClient;

/**
 * HUD 拖动编辑模式（Lunar 风格）：/skyscript hud 切换。
 * 开启后 HUD 显示高亮框，鼠标按住 HUD 拖动即可调整位置，松开自动保存。
 */
public final class HudEditor {

    public static boolean active;
    private static boolean dragging;
    private static int dragOffsetX, dragOffsetY;

    private HudEditor() {
    }

    public static void toggle() {
        active = !active;
        if (!active) {
            SkyScriptConfig.save();
        }
        dragging = false;
    }

    /** 每 tick 处理拖动（由 SkyScriptClient 调用） */
    public static void tick(MinecraftClient c) {
        if (!active || c.currentScreen != null) return;
        boolean left = MouseButtons.isLeftDown();
        int sw = c.getWindow().getScaledWidth();
        int sh = c.getWindow().getScaledHeight();
        double mx = c.mouse.getX() * sw / (double) c.getWindow().getWidth();
        double my = c.mouse.getY() * sh / (double) c.getWindow().getHeight();

        if (left) {
            if (!dragging) {
                int[] r = ScriptHud.getRect(c);
                if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                    dragging = true;
                    dragOffsetX = (int) (mx - r[0]);
                    dragOffsetY = (int) (my - r[1]);
                }
            }
            if (dragging) {
                var hud = SkyScriptConfig.get().hud;
                hud.x = (int) (mx - dragOffsetX);
                hud.y = (int) (my - dragOffsetY);
            }
        } else if (dragging) {
            dragging = false;
            SkyScriptConfig.save();
        }
    }
}

package dev.skyscript.hud;

/**
 * HUD 编辑模式标志：由 HudEditScreen 开关。true 时 ScriptHud 会画高亮框。
 * 拖动交互在 HudEditScreen 里做（游戏里鼠标被抓住看视角，不能拖，必须开屏幕）。
 */
public final class HudEditor {

    public static boolean active;

    private HudEditor() {
    }
}

package dev.skyscript.hud;

import dev.skyscript.AttackModeHelper;
import dev.skyscript.config.Settings;
import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.engine.ScriptEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * Lunar 风格 HUD：模板文本 + 占位符，可调位置/缩放/背景，支持静默模式。
 * 占位符：{state} {script} {step} {col} {total} {timeLeft} {attackMode}
 * 整行颜色随状态变化：运行=绿 / 暂停=黄 / 空闲=灰，一眼可见。
 */
public final class ScriptHud {

    private ScriptHud() {
    }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker deltaTracker) {
        Minecraft c = Minecraft.getInstance();
        Settings.HudSettings h = SkyScriptConfig.get().hud;
        // 编辑模式下即使 HUD 被关闭也要显示，让用户能拖到位置
        if ((!h.enabled || h.silent) && !HudEditor.active) return;
        if (c.player == null || c.level == null) return;

        String text = format(h.template);
        if (text == null || text.isEmpty()) return;
        String debugLine = ScriptEngine.INSTANCE.getDebugLine();
        var tr = c.font;
        int[] rect = getRect(c);
        int x = rect[0], y = rect[1];

        Matrix3x2fStack ms = ctx.pose();
        ms.pushMatrix();
        float scale = h.scale <= 0 ? 1.0f : h.scale;
        ms.scale(scale, scale);
        int sx = (int) (x / scale);
        int sy = (int) (y / scale);
        int lines = debugLine == null ? 1 : 2;
        if (h.background) {
            ctx.fill(sx - 2, sy - 2, sx + Math.max(tr.width(text), debugLine == null ? 0 : tr.width(debugLine)) + 2,
                    sy + lines * tr.lineHeight + (lines > 1 ? 4 : 2), 0x80000000);
        }
        ctx.text(tr, stateCode(ScriptEngine.INSTANCE.getStateText()) + text, sx, sy, 0xFFFFFFFF);
        if (debugLine != null) {
            ctx.text(tr, "§8" + debugLine, sx, sy + tr.lineHeight + 2, 0xFFFFFFFF);
        }
        if (HudEditor.active) {
            // 编辑模式：白色边框 + 提示
            ctx.fill(sx - 3, sy - 3, sx + tr.width(text) + 3, sy - 2, 0xFFFFFFFF);
            ctx.fill(sx - 3, sy + tr.lineHeight + 2, sx + tr.width(text) + 3, sy + tr.lineHeight + 3, 0xFFFFFFFF);
            ctx.fill(sx - 3, sy - 3, sx - 2, sy + tr.lineHeight + 3, 0xFFFFFFFF);
            ctx.fill(sx + tr.width(text) + 2, sy - 3, sx + tr.width(text) + 3, sy + tr.lineHeight + 3, 0xFFFFFFFF);
            ctx.text(tr, "拖动调整位置 · §eESC§f 退出", sx, sy + tr.lineHeight + 6, 0xFFFFFFFF);
        }
        ms.popMatrix();
    }

    private static String stateCode(String state) {
        return switch (state) {
            case "运行" -> "§a";
            case "暂停" -> "§e";
            case "待命" -> "§b";
            default -> "§7";
        };
    }

    /** 当前 HUD 在屏幕上的可视矩形（缩放坐标）：{x, y, w, h}。
     *  统一语义：h.x/h.y 是绝对左上角（拖动/预设跳角都直接写它），预设不再叠加偏移。 */
    public static int[] getRect(Minecraft c) {
        Settings.HudSettings h = SkyScriptConfig.get().hud;
        String text = format(h.template);
        var tr = c.font;
        float scale = h.scale <= 0 ? 1.0f : h.scale;
        int textW = (int) (tr.width(text) * scale);
        int textH = (int) (tr.lineHeight * scale);
        return new int[]{h.x, h.y, textW, textH};
    }

    public static String format(String template) {
        if (template == null) template = "";
        ScriptEngine e = ScriptEngine.INSTANCE;
        int[] prog = e.getLoopProgress();
        String col = prog[0] < 0 ? "—" : String.valueOf(prog[0]);
        String total = prog[1] < 0 ? "—" : String.valueOf(prog[1]);
        int left = e.getTimeLeftSeconds();
        return template
                .replace("{state}", e.getStateText())
                .replace("{script}", e.getScript() == null ? "—" : e.getScript().name)
                .replace("{step}", e.getStepSummary())
                .replace("{col}", col)
                .replace("{total}", total)
                .replace("{timeLeft}", left < 0 ? "—" : String.valueOf(left))
                .replace("{attackMode}", AttackModeHelper.available() ? (AttackModeHelper.isToggle() ? "切换" : "长按") : "—");
    }
}

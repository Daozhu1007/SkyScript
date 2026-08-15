package dev.skyscript.hud;

import dev.skyscript.AttackModeHelper;
import dev.skyscript.config.Settings;
import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.engine.ScriptEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

/**
 * Lunar 风格 HUD：模板文本 + 占位符，可调位置/缩放/背景，支持静默模式。
 * 占位符：{state} {script} {step} {col} {total} {timeLeft} {attackMode}
 * 整行颜色随状态变化：运行=绿 / 暂停=黄 / 空闲=灰，一眼可见。
 */
public final class ScriptHud {

    private ScriptHud() {
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient c = MinecraftClient.getInstance();
        Settings.HudSettings h = SkyScriptConfig.get().hud;
        // 编辑模式下即使 HUD 被关闭也要显示，让用户能拖到位置
        if ((!h.enabled || h.silent) && !HudEditor.active) return;
        if (c.player == null || c.world == null) return;

        String text = format(h.template);
        if (text == null || text.isEmpty()) return;
        TextRenderer tr = c.textRenderer;
        int[] rect = getRect(c);
        int x = rect[0], y = rect[1];

        Matrix3x2fStack ms = ctx.getMatrices();
        ms.pushMatrix();
        float scale = h.scale <= 0 ? 1.0f : h.scale;
        ms.scale(scale, scale);
        int sx = (int) (x / scale);
        int sy = (int) (y / scale);
        if (h.background) {
            ctx.fill(sx - 2, sy - 2, sx + tr.getWidth(text) + 2, sy + tr.fontHeight + 2, 0x80000000);
        }
        ctx.drawText(tr, text, sx, sy, stateColor(ScriptEngine.INSTANCE.getStateText()), true);
        if (HudEditor.active) {
            // 编辑模式：白色边框 + 提示
            ctx.fill(sx - 3, sy - 3, sx + tr.getWidth(text) + 3, sy - 2, 0xFFFFFFFF);
            ctx.fill(sx - 3, sy + tr.fontHeight + 2, sx + tr.getWidth(text) + 3, sy + tr.fontHeight + 3, 0xFFFFFFFF);
            ctx.fill(sx - 3, sy - 3, sx - 2, sy + tr.fontHeight + 3, 0xFFFFFFFF);
            ctx.fill(sx + tr.getWidth(text) + 2, sy - 3, sx + tr.getWidth(text) + 3, sy + tr.fontHeight + 3, 0xFFFFFFFF);
            ctx.drawText(tr, Text.literal("拖动调整位置 · §e/skyscript hud§f 退出"), sx, sy + tr.fontHeight + 6, 0xFFFFFF, true);
        }
        ms.popMatrix();
    }

    /** 当前 HUD 在屏幕上的可视矩形（缩放坐标）：{x, y, w, h} */
    public static int[] getRect(MinecraftClient c) {
        Settings.HudSettings h = SkyScriptConfig.get().hud;
        String text = format(h.template);
        TextRenderer tr = c.textRenderer;
        float scale = h.scale <= 0 ? 1.0f : h.scale;
        int textW = (int) (tr.getWidth(text) * scale);
        int textH = (int) (tr.fontHeight * scale);
        int sw = c.getWindow().getScaledWidth();
        int sh = c.getWindow().getScaledHeight();
        int x = h.x;
        int y = h.y;
        switch (h.pos == null ? "top-left" : h.pos) {
            case "top-right" -> x = sw - textW - h.x;
            case "bottom-left" -> y = sh - textH - h.y;
            case "bottom-right" -> {
                x = sw - textW - h.x;
                y = sh - textH - h.y;
            }
            default -> {
            }
        }
        return new int[]{x, y, textW, textH};
    }

    private static int stateColor(String state) {
        return switch (state) {
            case "运行" -> 0xFF55FF55;
            case "暂停" -> 0xFFFFFF55;
            case "待命" -> 0xFF55FFFF;   // 已开启(F8 arm)但脚本未运行
            default -> 0xFFAAAAAA;
        };
    }

    static String format(String template) {
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

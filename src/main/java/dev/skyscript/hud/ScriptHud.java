package dev.skyscript.hud;

import dev.skyscript.AttackModeHelper;
import dev.skyscript.config.Settings;
import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.engine.ScriptEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
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
        if (!h.enabled || h.silent) return;
        if (c.player == null || c.world == null) return;

        String text = format(h.template);
        if (text == null || text.isEmpty()) return;
        TextRenderer tr = c.textRenderer;
        int textW = tr.getWidth(text);
        int textH = tr.fontHeight;
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

        Matrix3x2fStack ms = ctx.getMatrices();
        ms.pushMatrix();
        float scale = h.scale <= 0 ? 1.0f : h.scale;
        ms.scale(scale, scale);
        int sx = (int) (x / scale);
        int sy = (int) (y / scale);
        if (h.background) {
            ctx.fill(sx - 2, sy - 2, sx + textW + 2, sy + textH + 2, 0x80000000);
        }
        ctx.drawText(tr, text, sx, sy, stateColor(ScriptEngine.INSTANCE.getStateText()), true);
        ms.popMatrix();
    }

    private static int stateColor(String state) {
        return switch (state) {
            case "运行" -> 0xFF55FF55;
            case "暂停" -> 0xFFFFFF55;
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

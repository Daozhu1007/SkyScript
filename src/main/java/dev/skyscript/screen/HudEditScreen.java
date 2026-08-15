package dev.skyscript.screen;

import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.hud.HudEditor;
import dev.skyscript.hud.ScriptHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * HUD 位置/大小编辑屏幕（Lunar 风格）：游戏画面在背后，光标自由。
 * 拖动 HUD 移动位置；四角白色手柄拖动可缩放；滚轮直接调大小；松开自动保存，ESC 退出。
 */
public class HudEditScreen extends Screen {

    private static final int HANDLE = 4; // 手柄半宽

    private boolean dragging;      // 移动 HUD
    private int moveOffsetX, moveOffsetY;
    private int resizeCorner = -1; // 0=左上 1=右上 2=左下 3=右下

    public HudEditScreen() {
        super(Text.literal("HUD 编辑"));
        HudEditor.active = true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        MinecraftClient c = MinecraftClient.getInstance();
        ScriptHud.render(ctx);
        int[] r = ScriptHud.getRect(c);
        // 四角手柄
        int[][] corners = {
                {r[0], r[1]}, {r[0] + r[2], r[1]},
                {r[0], r[1] + r[3]}, {r[0] + r[2], r[1] + r[3]}
        };
        for (int[] cp : corners) {
            ctx.fill(cp[0] - HANDLE, cp[1] - HANDLE, cp[0] + HANDLE, cp[1] + HANDLE, 0xFFFFFFFF);
        }
        // 底部小提示
        ctx.getTextConsumer().text(8, c.getWindow().getScaledHeight() - 12,
                Text.literal("拖动移动 · 拖角缩放 · 滚轮调大小 · §eESC§f 退出"));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        var hud = SkyScriptConfig.get().hud;
        float scale = hud.scale <= 0 ? 1.0f : hud.scale;
        hud.scale = clamp(scale + (float) verticalAmount * 0.1f);
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            int mx = (int) click.x();
            int my = (int) click.y();
            int[] r = ScriptHud.getRect(MinecraftClient.getInstance());
            int[][] corners = {
                    {r[0], r[1]}, {r[0] + r[2], r[1]},
                    {r[0], r[1] + r[3]}, {r[0] + r[2], r[1] + r[3]}
            };
            for (int i = 0; i < 4; i++) {
                if (mx >= corners[i][0] - HANDLE - 2 && mx <= corners[i][0] + HANDLE + 2
                        && my >= corners[i][1] - HANDLE - 2 && my <= corners[i][1] + HANDLE + 2) {
                    resizeCorner = i;
                    return true;
                }
            }
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                dragging = true;
                moveOffsetX = mx - r[0];
                moveOffsetY = my - r[1];
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (resizeCorner >= 0) {
            resizeTo((int) click.x(), (int) click.y());
            return true;
        }
        if (dragging) {
            var hud = SkyScriptConfig.get().hud;
            hud.x = (int) click.x() - moveOffsetX;
            hud.y = (int) click.y() - moveOffsetY;
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging || resizeCorner >= 0) {
            dragging = false;
            resizeCorner = -1;
            SkyScriptConfig.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        HudEditor.active = false;
        SkyScriptConfig.save();
        super.close();
    }

    /** 拖动角手柄缩放：锚定对角，按新宽度算 scale，并重摆 x/y 使对角不动 */
    private void resizeTo(int mx, int my) {
        MinecraftClient c = MinecraftClient.getInstance();
        var hud = SkyScriptConfig.get().hud;
        int[] r = ScriptHud.getRect(c);
        float oldScale = hud.scale <= 0 ? 1.0f : hud.scale;
        int textW = Math.max(1, (int) (r[2] / oldScale)); // 未缩放文字宽
        int textH = Math.max(1, (int) (r[3] / oldScale));
        int lx = r[0], ly = r[1], rx = r[0] + r[2], ry = r[1] + r[3];
        float scale = hud.scale;
        switch (resizeCorner) {
            case 0 -> { // 左上：锚定右下
                scale = clamp((rx - mx) / (float) textW);
                hud.x = rx - (int) (textW * scale);
                hud.y = ry - (int) (textH * scale);
            }
            case 1 -> { // 右上：锚定左下
                scale = clamp((mx - lx) / (float) textW);
                hud.x = lx;
                hud.y = ry - (int) (textH * scale);
            }
            case 2 -> { // 左下：锚定右上
                scale = clamp((rx - mx) / (float) textW);
                hud.x = rx - (int) (textW * scale);
                hud.y = ly;
            }
            case 3 -> { // 右下：锚定左上
                scale = clamp((mx - lx) / (float) textW);
                hud.x = lx;
                hud.y = ly;
            }
            default -> {
            }
        }
        hud.scale = scale;
    }

    private static float clamp(float s) {
        return Math.max(0.4f, Math.min(4.0f, s));
    }
}

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
 * HUD 位置编辑屏幕（Lunar 风格）：打开后游戏画面仍在背后，光标自由，
 * 按住 HUD 拖动调整位置，松开自动保存，ESC 退出。
 * （不能直接在游戏世界里拖——那里鼠标被抓住看视角，没有自由光标。）
 */
public class HudEditScreen extends Screen {

    private boolean dragging;
    private int offsetX, offsetY;

    public HudEditScreen() {
        super(Text.literal("HUD 位置编辑"));
        HudEditor.active = true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ScriptHud.render(ctx);
        ctx.getTextConsumer().text(8, 8, Text.literal("§eHUD 编辑：按住 HUD 拖动位置，松开保存 · §fESC§e 退出"));
        ctx.getTextConsumer().text(8, 20, Text.literal("§7也可以直接改下面的偏移值："));
        int[] r = ScriptHud.getRect(MinecraftClient.getInstance());
        var hud = SkyScriptConfig.get().hud;
        ctx.getTextConsumer().text(8, 32, Text.literal("X 偏移: " + hud.x + "   Y 偏移: " + hud.y + "   （可视矩形 " + r[0] + "," + r[1] + " → " + (r[0] + r[2]) + "," + (r[1] + r[3]) + "）"));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            int[] r = ScriptHud.getRect(MinecraftClient.getInstance());
            int mx = (int) click.x();
            int my = (int) click.y();
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                dragging = true;
                offsetX = mx - r[0];
                offsetY = my - r[1];
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging) {
            var hud = SkyScriptConfig.get().hud;
            hud.x = (int) click.x() - offsetX;
            hud.y = (int) click.y() - offsetY;
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging) {
            dragging = false;
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
}

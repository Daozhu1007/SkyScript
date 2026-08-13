package dev.skyscript.screen;

import dev.skyscript.script.Step;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 步骤列表：脚本 steps 或 loop 的 body 都复用这个 Screen。
 * 行内操作：↑ 下移 / ↓ 上移 / 编辑 / 子步骤(loop) / 删除。
 * 「完成」时调用 onSave（脚本级保存；嵌套 loop body 传 no-op，由脚本级统一落盘）。
 */
public class StepListScreen extends Screen {

    private static final int ROW_H = 24;
    private static final int ZONE_W = 44;

    private final List<Step> steps;
    private final Runnable onSave;
    private final String contextTitle;
    private int scroll = 0;
    private int deleteArm = -1;

    public StepListScreen(String contextTitle, List<Step> steps, Runnable onSave) {
        super(Text.literal(contextTitle));
        this.steps = steps;
        this.onSave = onSave;
        this.contextTitle = contextTitle;
    }

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;
        addDrawableChild(ButtonWidget.builder(Text.literal("添加步骤"), b -> {
            Step step = new Step();
            this.client.setScreen(new StepEditScreen(step, s -> {
                steps.add(s);
                refresh();
            }));
        }).dimensions(w - 300, h - 30, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("完成"), b -> {
            onSave.run();
            close();
        }).dimensions(w - 90, h - 30, 70, 20).build());
        int max = Math.max(0, steps.size() * ROW_H - (h - 60));
        if (scroll > max) scroll = max;
    }

    private void refresh() {
        clearChildren();
        init();
    }

    private int rowY(int i) {
        return 32 + i * ROW_H - scroll;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        int zoneStart = this.width - 16 - ZONE_W * 5 - 16;
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            int y = rowY(i);
            if (y < -ROW_H || y > this.height) continue;
            ctx.drawText(this.textRenderer, Text.literal("§7" + (i + 1) + ". §f" + s.summary()), 12, y, 0xFFFFFF, false);
            int col = 0;
            if (i > 0) drawZone(ctx, zoneStart, y, col++, "↑", 0xAAAAAA);
            if (i < steps.size() - 1) drawZone(ctx, zoneStart, y, col++, "↓", 0xAAAAAA);
            drawZone(ctx, zoneStart, y, col++, "编辑", 0xAAAAAA);
            if ("loop".equals(s.type)) drawZone(ctx, zoneStart, y, col++, "子步骤", 0x55FFFF);
            drawZone(ctx, zoneStart, y, col, deleteArm == i ? "确认?" : "删除", 0xFF5555);
        }
        if (steps.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7还没有步骤，点右下角「添加步骤」"), this.width / 2, this.height / 2, 0xFFFFFF);
        }
    }

    private void drawZone(DrawContext ctx, int zoneStart, int y, int idx, String label, int color) {
        int x = zoneStart + idx * (ZONE_W + 4);
        ctx.fill(x, y - 2, x + ZONE_W, y + 12, 0x40000000);
        ctx.drawText(this.textRenderer, Text.literal(label), x + 4, y, color, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mouseX = click.x();
            double mouseY = click.y();
            int zoneStart = this.width - 16 - ZONE_W * 5 - 16;
            for (int i = 0; i < steps.size(); i++) {
                int y = rowY(i);
                if (mouseY >= y - 2 && mouseY < y + 14) {
                    if (mouseX >= zoneStart && mouseX < zoneStart + (ZONE_W + 4) * 5) {
                        int idx = (int) ((mouseX - zoneStart) / (ZONE_W + 4));
                        handleZone(i, idx);
                        return true;
                    }
                    // 点行主体 → 编辑
                    Step s = steps.get(i);
                    this.client.setScreen(new StepEditScreen(s, ss -> refresh()));
                    return true;
                }
            }
            deleteArm = -1;
        }
        return super.mouseClicked(click, doubled);
    }

    private void handleZone(int i, int idx) {
        Step s = steps.get(i);
        // 有效区依次为：↑(若有) ↓(若有) 编辑 子步骤(loop) 删除 —— 用与 render 相同的列号推导
        int col = 0;
        if (i > 0) {
            if (idx == col++) {
                move(i, i - 1);
                return;
            }
        }
        if (i < steps.size() - 1) {
            if (idx == col++) {
                move(i, i + 1);
                return;
            }
        }
        if (idx == col++) {
            this.client.setScreen(new StepEditScreen(s, ss -> refresh()));
            return;
        }
        if ("loop".equals(s.type)) {
            if (idx == col++) {
                this.client.setScreen(new StepListScreen(contextTitle + " › 循环", s.body, () -> {
                }));
                return;
            }
        }
        if (idx == col) {
            if (deleteArm == i) {
                steps.remove(i);
                deleteArm = -1;
                refresh();
            } else {
                deleteArm = i;
            }
        }
    }

    private void move(int from, int to) {
        Step s = steps.remove(from);
        steps.add(to, s);
        refresh();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (int) (verticalAmount * 12));
        int max = Math.max(0, steps.size() * ROW_H - (this.height - 60));
        if (scroll > max) scroll = max;
        return true;
    }
}

package dev.skyscript.screen;

import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.script.Script;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 方案列表：查看 / 新建 / 设为活动 / 编辑 / 删除。
 * 点击方案名 → 进入步骤列表；行右侧三个操作区。
 */
public class ScriptListScreen extends Screen {

    private static final int ROW_H = 24;
    private static final int ZONE_W = 40;

    private final List<Script> scripts = new ArrayList<>();
    private int scroll = 0;
    private String deleteArm = "";

    public ScriptListScreen() {
        super(Text.literal("SkyScript 脚本方案"));
    }

    @Override
    protected void init() {
        scripts.clear();
        scripts.addAll(SkyScriptConfig.listScripts());
        int w = this.width;
        int h = this.height;
        addDrawableChild(ButtonWidget.builder(Text.literal("新建方案"), b -> {
            String name = uniqueName("新方案");
            Script s = new Script(name);
            s.steps = Script.createDefault(name).steps;
            SkyScriptConfig.saveScript(s);
            refresh();
        }).dimensions(w - 300, h - 30, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("设置"), b -> this.client.setScreen(new SettingsScreen()))
                .dimensions(12, h - 30, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("完成"), b -> close())
                .dimensions(w - 90, h - 30, 70, 20).build());
        int max = Math.max(0, scripts.size() * ROW_H - (h - 60));
        if (scroll > max) scroll = max;
    }

    private void refresh() {
        clearChildren();
        init();
    }

    private String uniqueName(String base) {
        String name = base;
        int n = 2;
        while (true) {
            boolean taken = false;
            for (Script s : scripts) {
                if (s.name.equals(name)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) return name;
            name = base + " " + n++;
        }
    }

    private int rowY(int i) {
        return 32 + i * ROW_H - scroll;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        int zoneStart = this.width - 16 - ZONE_W * 3 - 8;
        String active = SkyScriptConfig.get().activeScript;
        for (int i = 0; i < scripts.size(); i++) {
            Script s = scripts.get(i);
            int y = rowY(i);
            if (y < -ROW_H || y > this.height) continue;
            boolean isActive = s.name.equals(active);
            String info = s.loop == 0 ? " ∞" : " ×" + s.loop;
            ctx.drawText(this.textRenderer,
                    Text.literal((isActive ? "▶ " : "  ") + s.name + "  §7" + s.steps.size() + " 步" + (s.loop == 0 ? "§a" : "§7") + info),
                    12, y, 0xFFFFFF, false);
            drawZone(ctx, zoneStart, y, 0, "活动", isActive ? 0x55FF55 : 0xAAAAAA);
            drawZone(ctx, zoneStart, y, 1, "编辑", 0xAAAAAA);
            drawZone(ctx, zoneStart, y, 2, deleteArm.equals(s.name) ? "确认?" : "删除", 0xFF5555);
        }
        if (scripts.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7还没有方案，点右下角「新建方案」开始"), this.width / 2, this.height / 2, 0xFFFFFF);
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
            int zoneStart = this.width - 16 - ZONE_W * 3 - 8;
            for (int i = 0; i < scripts.size(); i++) {
                int y = rowY(i);
                if (mouseY >= y - 2 && mouseY < y + 14) {
                    Script s = scripts.get(i);
                    int zoneIdx = ((int) mouseX - zoneStart) / (ZONE_W + 4);
                    if (mouseX >= zoneStart && mouseX < zoneStart + ZONE_W * 3 + 8) {
                        if (zoneIdx == 0) {
                            SkyScriptConfig.get().activeScript = s.name;
                            SkyScriptConfig.save();
                            refresh();
                        } else if (zoneIdx == 1) {
                            this.client.setScreen(new StepListScreen(s.name + " 的步骤", s.steps, () -> SkyScriptConfig.saveScript(s)));
                        } else if (zoneIdx == 2) {
                            if (deleteArm.equals(s.name)) {
                                if (SkyScriptConfig.get().activeScript.equals(s.name)) {
                                    SkyScriptConfig.get().activeScript = "";
                                }
                                SkyScriptConfig.deleteScript(s.name);
                                deleteArm = "";
                                refresh();
                            } else {
                                deleteArm = s.name;
                            }
                        }
                        return true;
                    }
                    // 点名字 → 编辑步骤
                    this.client.setScreen(new StepListScreen(s.name + " 的步骤", s.steps, () -> SkyScriptConfig.saveScript(s)));
                    return true;
                }
            }
            deleteArm = "";
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (int) (verticalAmount * 12));
        int max = Math.max(0, scripts.size() * ROW_H - (this.height - 60));
        if (scroll > max) scroll = max;
        return true;
    }
}

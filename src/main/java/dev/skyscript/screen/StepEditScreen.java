package dev.skyscript.screen;

import dev.skyscript.input.KeyNames;
import dev.skyscript.script.PosCond;
import dev.skyscript.script.Step;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * 步骤编辑：按类型显示对应表单。
 * hold/press(hold)：按键组 + 结束条件(time/position/manual)
 * wait：毫秒   press：模式(tap/hold)   command：指令   loop：次数
 * 支持"录制按键"：点录制后按任意键直接录入。
 */
public class StepEditScreen extends Screen {

    private static final String[] TYPES = {"hold", "wait", "press", "command", "loop"};
    private static final String[] UNTILS = {"time", "position", "manual"};
    private static final String[] AXES = {"x", "y", "z"};
    private static final String[] OPS = {"<=", ">=", "<", ">", "=="};
    private static final String[] PRESS_MODES = {"tap", "hold"};

    private final Step step;
    private final Consumer<Step> onSave;
    private boolean capturing;
    private String msg = "";

    private String msText = "";
    private String timesText = "1";
    private String cmdText = "";
    private String posValueText = "0";

    public StepEditScreen(Step step, Consumer<Step> onSave) {
        super(Text.literal("编辑步骤"));
        this.step = step;
        this.onSave = onSave;
        msText = step.ms > 0 ? String.valueOf(step.ms) : "";
        timesText = step.times > 0 ? String.valueOf(step.times) : "1";
        cmdText = step.value == null ? "" : step.value;
        if (step.cond.isEmpty()) step.cond.add(new PosCond());
        PosCond pc = step.cond.get(0);
        posValueText = String.valueOf(pc.value);
    }

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;
        int cx = w / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("类型: " + step.type), b -> {
            step.type = next(TYPES, step.type);
            refresh();
        }).dimensions(cx - 100, 32, 200, 20).build());

        int y = 60;
        switch (step.type) {
            case "hold", "press" -> {
                addDrawableChild(ButtonWidget.builder(Text.literal("按键: " + step.keys), b -> {
                    capturing = true;
                }).dimensions(cx - 100, y, 140, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("录制"), b -> {
                    capturing = true;
                }).dimensions(cx + 48, y, 52, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("清除"), b -> {
                    step.keys.clear();
                    refresh();
                }).dimensions(cx + 104, y, 52, 20).build());
                y += 28;
                if ("press".equals(step.type)) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("模式: " + step.mode), b -> {
                        step.mode = next(PRESS_MODES, step.mode);
                        refresh();
                    }).dimensions(cx - 100, y, 200, 20).build());
                    y += 28;
                }
                addDrawableChild(ButtonWidget.builder(Text.literal("结束: " + step.untilType), b -> {
                    step.untilType = next(UNTILS, step.untilType);
                    refresh();
                }).dimensions(cx - 100, y, 200, 20).build());
                y += 28;
                if ("time".equals(step.untilType)) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("毫秒:"), b -> {
                    }).dimensions(cx - 100, y, 60, 20).build());
                    TextFieldWidget ms = new TextFieldWidget(this.textRenderer, cx - 36, y, 136, 20, Text.literal("毫秒"));
                    ms.setMaxLength(12);
                    ms.setText(msText);
                    ms.setChangedListener(s -> msText = s);
                    addDrawableChild(ms);
                } else if ("position".equals(step.untilType)) {
                    PosCond pc = step.cond.isEmpty() ? new PosCond() : step.cond.get(0);
                    addDrawableChild(ButtonWidget.builder(Text.literal("轴: " + pc.axis), b -> {
                        pc.axis = next(AXES, pc.axis);
                        refresh();
                    }).dimensions(cx - 100, y, 66, 20).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal(pc.op), b -> {
                        pc.op = next(OPS, pc.op);
                        refresh();
                    }).dimensions(cx - 28, y, 52, 20).build());
                    TextFieldWidget val = new TextFieldWidget(this.textRenderer, cx + 30, y, 70, 20, Text.literal("坐标值"));
                    val.setMaxLength(16);
                    val.setText(posValueText);
                    val.setChangedListener(s -> posValueText = s);
                    addDrawableChild(val);
                }
            }
            case "wait" -> {
                addDrawableChild(ButtonWidget.builder(Text.literal("毫秒:"), b -> {
                }).dimensions(cx - 100, y, 60, 20).build());
                TextFieldWidget ms = new TextFieldWidget(this.textRenderer, cx - 36, y, 136, 20, Text.literal("毫秒"));
                ms.setMaxLength(12);
                ms.setText(msText);
                ms.setChangedListener(s -> msText = s);
                addDrawableChild(ms);
            }
            case "command" -> {
                addDrawableChild(ButtonWidget.builder(Text.literal("指令:"), b -> {
                }).dimensions(cx - 100, y, 60, 20).build());
                TextFieldWidget cmd = new TextFieldWidget(this.textRenderer, cx - 36, y, 180, 20, Text.literal("如 /home"));
                cmd.setMaxLength(100);
                cmd.setText(cmdText);
                cmd.setChangedListener(s -> cmdText = s);
                addDrawableChild(cmd);
            }
            case "loop" -> {
                addDrawableChild(ButtonWidget.builder(Text.literal("次数:"), b -> {
                }).dimensions(cx - 100, y, 60, 20).build());
                TextFieldWidget times = new TextFieldWidget(this.textRenderer, cx - 36, y, 136, 20, Text.literal("循环次数"));
                times.setMaxLength(6);
                times.setText(timesText);
                times.setChangedListener(s -> timesText = s);
                addDrawableChild(times);
                y += 28;
                addDrawableChild(ButtonWidget.builder(Text.literal("编辑循环体 (" + step.body.size() + " 子步骤)"), b -> {
                    this.client.setScreen(new StepListScreen(this.title.getString() + " › 循环", step.body, () -> {
                    }));
                }).dimensions(cx - 100, y, 200, 20).build());
            }
            default -> {
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), b -> {
            applyFields();
            onSave.accept(step);
            close();
        }).dimensions(cx - 100, h - 30, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close())
                .dimensions(cx + 10, h - 30, 90, 20).build());
    }

    private void refresh() {
        clearChildren();
        init();
    }

    private static String next(String[] options, String current) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(current)) return options[(i + 1) % options.length];
        }
        return options[0];
    }

    private void applyFields() {
        try {
            if (msText != null && !msText.isEmpty()) step.ms = Math.max(0, Integer.parseInt(msText.trim()));
        } catch (NumberFormatException ignored) {
        }
        try {
            if (timesText != null && !timesText.isEmpty()) step.times = Math.max(1, Integer.parseInt(timesText.trim()));
        } catch (NumberFormatException ignored) {
        }
        step.value = cmdText;
        if (step.cond.isEmpty()) step.cond.add(new PosCond());
        try {
            step.cond.get(0).value = Double.parseDouble(posValueText.trim());
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (capturing) {
            if (input.isEscape()) {
                capturing = false;
            } else {
                String name = KeyNames.nameOf(input.key());
                if (!step.keys.contains(name)) step.keys.add(name);
                capturing = false;
                msg = "已录入 " + name;
                refresh();
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        String title = capturing ? "按任意键录入… (ESC 取消)" : (msg.isEmpty() ? "编辑步骤 (" + step.type + ")" : msg);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(title), this.width / 2, 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§7提示: hold=按住直到条件满足 · press tap=点按 · command 自动发送 · loop 嵌套循环"),
                this.width / 2, this.height - 52, 0xFFFFFF);
    }
}

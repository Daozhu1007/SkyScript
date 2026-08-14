package dev.skyscript.screen;

import dev.skyscript.config.Settings;
import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.input.KeyNames;
import dev.skyscript.script.PosCond;
import dev.skyscript.script.Script;
import dev.skyscript.script.Step;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * SkyScript 整合式控制面板（O / /skyscript 打开，Mod Menu 同样入口）。
 *
 * <p>左侧标签栏切换三个页：总控（运行 / F8 总控 / 高级）、HUD、脚本。
 * 脚本页内做"方案列表 → 步骤列表 → 步骤编辑"下钻，全程不出这个面板。
 * 设置项先改内存副本，点「保存并返回」才写盘；脚本改动在离开脚本页 / 关面板时写盘。
 */
public class SkyScriptScreen extends Screen {

    public enum Tab { MAIN, HUD, SCRIPTS }

    // ---- 布局常量 ----
    private static final int SIDEBAR_W = 118;
    private static final int CONTENT_X = 126;
    private static final int LABEL_X = CONTENT_X + 4;
    private static final int TOP = 24;
    private static final int ROW_H = 20;
    private static final int ZONE_W = 42;
    private static final int ZONE_GAP = 4;

    // 循环选择项：显示值 ↔ 存储值
    private static final String[][] POS = {{"左上", "top-left"}, {"右上", "top-right"}, {"左下", "bottom-left"}, {"右下", "bottom-right"}};
    private static final String[][] SEM = {{"停止", "stop"}, {"忽略", "ignore"}};
    private static final String[][] METH = {{"游戏内注入", "inject"}, {"系统级模拟", "os"}};
    private static final String[] TYPES = {"hold", "wait", "press", "command", "loop"};
    private static final String[] UNTILS = {"time", "position", "manual"};
    private static final String[] AXES = {"x", "y", "z"};
    private static final String[] OPS = {"<=", ">=", "<", ">", "=="};
    private static final String[] PRESS_MODES = {"tap", "hold"};

    private Tab tab;
    private int scroll;
    /** 渲染项：{y, text, isSection}，init 里构建，render 里逐帧画 */
    private final List<Object[]> renderItems = new ArrayList<>();
    private int nextRow; // 行号游标（含区块标题行）

    // ---- 设置编辑副本 ----
    private boolean hudEnabled, hudSilent, hudBackground;
    private String hudPos, hudX, hudY, hudScale, hudTemplate;
    private String curKeySem, triggerKeys, masterKeyName, settingsKeyName;
    private boolean directionSwap;
    private boolean mToggleScript, mToggleAttack, mToggleHud, mFeedback, mArmedOnJoin;
    private String extKey, extMethod;

    // ---- 脚本下钻状态 ----
    private List<Script> scripts = new ArrayList<>();
    private Script editingScript;                        // null = 方案列表；非 null = 在步骤层
    private final Deque<List<Step>> stepsStack = new ArrayDeque<>(); // 步骤层栈：底=script.steps，上=循环体
    private Step editingStep;                            // 非 null = 步骤编辑层
    private String deleteArm = "";
    private int stepDeleteArm = -1;
    private boolean capturing;
    // 步骤编辑表单临时文本
    private String msText = "", timesText = "1", cmdText = "", posValueText = "0";

    public SkyScriptScreen() {
        this(Tab.MAIN);
    }

    public SkyScriptScreen(Tab initialTab) {
        super(Text.literal("SkyScript 控制台"));
        this.tab = initialTab;
        loadFromConfig();
        refreshScripts();
    }

    // ==================== 配置加载 / 保存 ====================

    private void loadFromConfig() {
        Settings s = SkyScriptConfig.get();
        hudEnabled = s.hud.enabled;
        hudSilent = s.hud.silent;
        hudBackground = s.hud.background;
        hudPos = s.hud.pos;
        hudX = String.valueOf(s.hud.x);
        hudY = String.valueOf(s.hud.y);
        hudScale = String.valueOf(s.hud.scale);
        hudTemplate = s.hud.template;
        curKeySem = s.currentKeySemantics;
        triggerKeys = String.join(", ", s.triggerKeys);
        masterKeyName = s.masterKeyName;
        settingsKeyName = s.settingsKeyName;
        directionSwap = s.directionSwap;
        mToggleScript = s.master.toggleScript;
        mToggleAttack = s.master.toggleAttackMode;
        mToggleHud = s.master.toggleHud;
        mFeedback = s.master.feedback;
        mArmedOnJoin = s.master.armedOnJoin;
        extKey = s.master.externalKeys.isEmpty() ? "" : s.master.externalKeys.get(0).key;
        extMethod = s.master.externalKeys.isEmpty() ? "inject" : s.master.externalKeys.get(0).method;
    }

    private void saveSettings() {
        Settings s = SkyScriptConfig.get();
        s.hud.enabled = hudEnabled;
        s.hud.silent = hudSilent;
        s.hud.background = hudBackground;
        s.hud.pos = hudPos;
        s.hud.x = parseInt(hudX, 4);
        s.hud.y = parseInt(hudY, 4);
        s.hud.scale = parseFloat(hudScale, 1.0f);
        s.hud.template = hudTemplate;
        s.currentKeySemantics = curKeySem;
        s.triggerKeys = parseKeys(triggerKeys);
        s.masterKeyName = masterKeyName.trim().toUpperCase();
        s.settingsKeyName = settingsKeyName.trim().toUpperCase();
        s.directionSwap = directionSwap;
        s.master.toggleScript = mToggleScript;
        s.master.toggleAttackMode = mToggleAttack;
        s.master.toggleHud = mToggleHud;
        s.master.feedback = mFeedback;
        s.master.armedOnJoin = mArmedOnJoin;
        if (s.master.externalKeys.isEmpty()) s.master.externalKeys.add(new Settings.MasterSettings.ExternalKey());
        s.master.externalKeys.get(0).key = extKey.trim().toUpperCase();
        s.master.externalKeys.get(0).method = extMethod;
        SkyScriptConfig.save();
    }

    private void refreshScripts() {
        scripts.clear();
        scripts.addAll(SkyScriptConfig.listScripts());
    }

    /** 若正在编辑脚本则写盘（离开脚本页 / 关面板时调用） */
    private void persistEditingScript() {
        if (editingScript != null) {
            SkyScriptConfig.saveScript(editingScript);
        }
    }

    // ==================== 布局助手 ====================

    private void refresh() {
        clearChildren();
        init();
    }

    private int yOf(int row) {
        return TOP + row * ROW_H - scroll;
    }

    private boolean visible(int y) {
        return y >= TOP - 8 && y <= this.height - 44;
    }

    private int ctrlX() {
        return CONTENT_X + 150;
    }

    private int ctrlW() {
        return this.width - ctrlX() - 14;
    }

    /** 区块标题 */
    private void addSection(String text) {
        int y = yOf(nextRow++);
        renderItems.add(new Object[]{y, "§e§l" + text, 1});
    }

    /** 纯文字行（列表项等） */
    private void addLabel(String text, int color) {
        int y = yOf(nextRow++);
        renderItems.add(new Object[]{y, text, color});
    }

    private void toggleRow(String label, boolean value, Consumer<Boolean> on) {
        int y = yOf(nextRow++);
        renderItems.add(new Object[]{y, "§7" + label, 0});
        if (!visible(y)) return;
        addDrawableChild(ButtonWidget.builder(Text.literal(value ? "§a开" : "§c关"), b -> on.accept(!value))
                .dimensions(ctrlX(), y, 60, 20).build());
    }

    private void cycleRow(String label, String value, String[][] map, Consumer<String> on) {
        int y = yOf(nextRow++);
        renderItems.add(new Object[]{y, "§7" + label, 0});
        if (!visible(y)) return;
        int idx = 0;
        for (int i = 0; i < map.length; i++) {
            if (map[i][1].equals(value)) { idx = i; break; }
        }
        int next = (idx + 1) % map.length;
        addDrawableChild(ButtonWidget.builder(Text.literal(map[idx][0]), b -> on.accept(map[next][1]))
                .dimensions(ctrlX(), y, 120, 20).build());
    }

    private void textRow(String label, String initial, Consumer<String> on, String hint) {
        int y = yOf(nextRow++);
        renderItems.add(new Object[]{y, "§7" + label, 0});
        if (visible(y)) {
            TextFieldWidget tf = new TextFieldWidget(this.textRenderer, ctrlX(), y, ctrlW(), 20, Text.literal(""));
            tf.setMaxLength(200);
            tf.setText(initial);
            tf.setChangedListener(on);
            addDrawableChild(tf);
        }
        if (hint != null) {
            int hy = yOf(nextRow++);
            renderItems.add(new Object[]{hy, "§8" + hint, 2});
        }
    }

    // ==================== init / 构建各页 ====================

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;

        // 左侧标签栏
        int ty = TOP;
        for (Tab t : Tab.values()) {
            String label = switch (t) {
                case MAIN -> "总控";
                case HUD -> "HUD";
                case SCRIPTS -> "脚本";
            };
            final Tab target = t;
            String text = (tab == t ? "▶ " : "  ") + label;
            addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> switchTab(target))
                    .dimensions(6, ty, SIDEBAR_W - 12, 24).build());
            ty += 30;
        }

        // 左侧底部动作按钮
        addDrawableChild(ButtonWidget.builder(Text.literal("保存并返回"), b -> {
            saveSettings();
            persistEditingScript();
            close();
        }).dimensions(6, h - 92, SIDEBAR_W - 12, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("恢复默认"), b -> {
            SkyScriptConfig.get().resetToDefaults();
            loadFromConfig();
            refresh();
        }).dimensions(6, h - 66, SIDEBAR_W - 12, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> {
            persistEditingScript();
            close();
        }).dimensions(6, h - 40, SIDEBAR_W - 12, 20).build());

        renderItems.clear();
        nextRow = 0;
        switch (tab) {
            case MAIN -> buildMainTab();
            case HUD -> buildHudTab();
            case SCRIPTS -> buildScriptsTab();
        }
        // 滚动上限
        int max = Math.max(0, nextRow * ROW_H - (h - 64));
        if (scroll > max) scroll = max;
    }

    private void switchTab(Tab target) {
        if (target == tab) return;
        if (tab == Tab.SCRIPTS) {
            persistEditingScript(); // 离开脚本页前保存
            editingScript = null;
            stepsStack.clear();
            editingStep = null;
        }
        tab = target;
        scroll = 0;
        refresh();
    }

    private void buildMainTab() {
        addSection("运行");
        toggleRow("进游戏自动开启", mArmedOnJoin, v -> { mArmedOnJoin = v; refresh(); });
        textRow("触发键（点击启动）", triggerKeys, v -> triggerKeys = v, "逗号分隔，如 A, D");
        cycleRow("运行中按当前方向键", curKeySem, SEM, v -> { curKeySem = v; refresh(); });
        addSection("F8 总控");
        toggleRow("F8 控制脚本开关", mToggleScript, v -> { mToggleScript = v; refresh(); });
        toggleRow("F8 切换攻击/摧毁模式", mToggleAttack, v -> { mToggleAttack = v; refresh(); });
        toggleRow("F8 联动 HUD 显示", mToggleHud, v -> { mToggleHud = v; refresh(); });
        textRow("外部热键（联动触发）", extKey, v -> extKey = v, "如 PGDN，可留空");
        cycleRow("外部热键方式", extMethod, METH, v -> { extMethod = v; refresh(); });
        toggleRow("聊天反馈消息", mFeedback, v -> { mFeedback = v; refresh(); });
        addSection("按键");
        textRow("控制台键", settingsKeyName, v -> settingsKeyName = v, "打开本面板的按键");
        textRow("总控键", masterKeyName, v -> masterKeyName = v, "F8：开启/关闭自动化");
        addSection("高级");
        toggleRow("方向交换（诊断兜底）", directionSwap, v -> { directionSwap = v; refresh(); });
    }

    private void buildHudTab() {
        addSection("HUD");
        toggleRow("显示 HUD", hudEnabled, v -> { hudEnabled = v; refresh(); });
        toggleRow("静默模式（不显示不提示）", hudSilent, v -> { hudSilent = v; refresh(); });
        cycleRow("位置", hudPos, POS, v -> { hudPos = v; refresh(); });
        textRow("水平偏移 X", hudX, v -> hudX = v, null);
        textRow("垂直偏移 Y", hudY, v -> hudY = v, null);
        textRow("缩放", hudScale, v -> hudScale = v, null);
        toggleRow("背景", hudBackground, v -> { hudBackground = v; refresh(); });
        textRow("显示模板", hudTemplate, v -> hudTemplate = v, "占位: {state} {script} {step} {timeLeft}s {attackMode}");
    }

    private void buildScriptsTab() {
        if (editingScript == null) {
            buildScriptList();
        } else if (editingStep == null) {
            buildStepList();
        } else {
            buildStepEdit();
        }
    }

    // ---------- 方案列表 ----------

    private void buildScriptList() {
        refreshScripts();
        addSection("脚本方案");
        addLabel("§7活动: " + (activeScriptName()), 0xFFFFFF);

        // 新建方案
        addDrawableChild(ButtonWidget.builder(Text.literal("新建方案"), b -> {
            String name = uniqueName("新方案");
            Script s = new Script(name);
            s.steps = Script.createDefault(name).steps;
            SkyScriptConfig.saveScript(s);
            refresh();
        }).dimensions(CONTENT_X, this.height - 34, 90, 20).build());

        int zoneStart = this.width - 16 - ZONE_W * 3 - ZONE_GAP * 2;
        for (int i = 0; i < scripts.size(); i++) {
            Script s = scripts.get(i);
            int y = yOf(nextRow++);
            boolean isActive = s.name.equals(activeScriptName());
            renderItems.add(new Object[]{y,
                    (isActive ? "§a▶ " : "  ") + "§f" + s.name + "  §7" + s.steps.size() + "步" + (s.loop == 0 ? " §a∞" : " §7×" + s.loop), 0});
            if (!visible(y)) continue;
            addDrawableChild(zoneBtn(zoneStart, y, 0, isActive ? "§a活动" : "活动",
                    b -> { SkyScriptConfig.get().activeScript = s.name; SkyScriptConfig.save(); refresh(); }));
            addDrawableChild(zoneBtn(zoneStart, y, 1, "编辑",
                    b -> { editingScript = s; stepsStack.clear(); stepsStack.push(s.steps); editingStep = null; scroll = 0; refresh(); }));
            addDrawableChild(zoneBtn(zoneStart, y, 2, deleteArm.equals(s.name) ? "确认?" : "§c删除",
                    b -> {
                        if (deleteArm.equals(s.name)) {
                            if (s.name.equals(SkyScriptConfig.get().activeScript)) SkyScriptConfig.get().activeScript = "";
                            SkyScriptConfig.deleteScript(s.name);
                            deleteArm = "";
                        } else {
                            deleteArm = s.name;
                        }
                        refresh();
                    }));
        }
        if (scripts.isEmpty()) {
            addLabel("§7还没有方案，点右上「新建方案」开始", 0xFFFFFF);
        }
    }

    private String activeScriptName() {
        String a = SkyScriptConfig.get().activeScript;
        return (a == null || a.isEmpty()) ? "（未设置）" : a;
    }

    private ButtonWidget zoneBtn(int zoneStart, int y, int idx, String text, Consumer<ButtonWidget> on) {
        return ButtonWidget.builder(Text.literal(text), b -> on.accept(b))
                .dimensions(zoneStart + idx * (ZONE_W + ZONE_GAP), y - 2, ZONE_W, 20).build();
    }

    private String uniqueName(String base) {
        String name = base;
        int n = 2;
        while (true) {
            boolean taken = false;
            for (Script s : scripts) {
                if (s.name.equals(name)) { taken = true; break; }
            }
            if (!taken) return name;
            name = base + " " + n++;
        }
    }

    // ---------- 步骤列表 ----------

    private void buildStepList() {
        if (editingScript == null) return;
        List<Step> steps = stepsStack.peek();
        if (steps == null) return;
        String title = stepsTitle();

        addSection(title);
        // 添加步骤 / 返回
        addDrawableChild(ButtonWidget.builder(Text.literal("添加步骤"), b -> {
            Step step = new Step();
            step.cond.add(new PosCond()); // 保证 position 条件至少一项可编辑
            editingStep = step;
            steps.add(step);
            msText = "";
            timesText = "1";
            cmdText = "";
            posValueText = "0";
            capturing = false;
            refresh();
        }).dimensions(CONTENT_X, this.height - 34, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> {
            backFromSteps();
        }).dimensions(CONTENT_X + 96, this.height - 34, 70, 20).build());

        int zoneStart = this.width - 16 - ZONE_W * 5 - ZONE_GAP * 4;
        for (int i = 0; i < steps.size(); i++) {
            final int idx = i;
            Step st = steps.get(i);
            int y = yOf(nextRow++);
            renderItems.add(new Object[]{y, "§7" + (i + 1) + ". §f" + st.summary(), 0});
            if (!visible(y)) continue;
            int col = 0;
            if (i > 0) addDrawableChild(zoneBtn(zoneStart, y, col++, "↑", b -> moveStep(steps, idx, idx - 1)));
            if (i < steps.size() - 1) addDrawableChild(zoneBtn(zoneStart, y, col++, "↓", b -> moveStep(steps, idx, idx + 1)));
            addDrawableChild(zoneBtn(zoneStart, y, col++, "编辑", b -> openStepEdit(st, false)));
            if ("loop".equals(st.type)) {
                addDrawableChild(zoneBtn(zoneStart, y, col++, "§b子步骤", b -> { stepsStack.push(st.body); refresh(); }));
            }
            addDrawableChild(zoneBtn(zoneStart, y, col, stepDeleteArm == idx ? "确认?" : "§c删除",
                    b -> {
                        if (stepDeleteArm == idx) {
                            steps.remove(idx);
                            stepDeleteArm = -1;
                        } else {
                            stepDeleteArm = idx;
                        }
                        refresh();
                    }));
        }
        if (steps.isEmpty()) {
            addLabel("§7还没有步骤，点右下「添加步骤」", 0xFFFFFF);
        }
    }

    private String stepsTitle() {
        if (editingScript == null) return "";
        String t = "方案 › " + editingScript.name;
        for (int i = 1; i < stepsStack.size(); i++) t += " › 循环";
        return t;
    }

    private void backFromSteps() {
        if (editingScript == null) return;
        stepsStack.pop();
        if (stepsStack.isEmpty()) {
            persistEditingScript(); // 回到方案列表前保存
            editingScript = null;
        }
        scroll = 0;
        refresh();
    }

    private void moveStep(List<Step> steps, int from, int to) {
        Step s = steps.remove(from);
        steps.add(to, s);
        refresh();
    }

    private void openStepEdit(Step st, boolean addedNew) {
        editingStep = st;
        msText = st.ms > 0 ? String.valueOf(st.ms) : "";
        timesText = st.times > 0 ? String.valueOf(st.times) : "1";
        cmdText = st.value == null ? "" : st.value;
        if (st.cond.isEmpty()) st.cond.add(new PosCond());
        posValueText = String.valueOf(st.cond.get(0).value);
        capturing = false;
        scroll = 0;
        refresh();
    }

    // ---------- 步骤编辑 ----------

    private void buildStepEdit() {
        if (editingStep == null) return;
        Step st = editingStep;
        addSection("编辑步骤");

        cycleButton("类型", st.type, TYPES, v -> { st.type = v; if (!"loop".equals(v) && st.body.isEmpty()) st.body = new ArrayList<>(); refresh(); });

        switch (st.type) {
            case "hold", "press" -> {
                addSection("按键");
                addLabel("§7当前按键: " + (st.keys.isEmpty() ? "（空）" : st.keys.toString()), 0xFFFFFF);
                addDrawableChild(ButtonWidget.builder(Text.literal(capturing ? "…按任意键录入 (ESC 取消)" : "录制按键"), b -> capturing = true)
                        .dimensions(LABEL_X, yOf(nextRow++), 140, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("清除按键"), b -> { st.keys.clear(); refresh(); })
                        .dimensions(LABEL_X + 146, yOf(nextRow - 1), 90, 20).build());
                if ("press".equals(st.type)) {
                    cycleButton("模式", st.mode, PRESS_MODES, v -> { st.mode = v; refresh(); });
                }
                cycleButton("结束条件", st.untilType, UNTILS, v -> { st.untilType = v; refresh(); });
                if ("time".equals(st.untilType)) {
                    textRow("毫秒", msText, v -> msText = v, "按住/等待时长");
                } else if ("position".equals(st.untilType)) {
                    PosCond pc = st.cond.isEmpty() ? new PosCond() : st.cond.get(0);
                    int py = yOf(nextRow++);
                    if (visible(py)) {
                        addDrawableChild(ButtonWidget.builder(Text.literal("轴: " + pc.axis), b -> {
                            pc.axis = next(AXES, pc.axis); refresh();
                        }).dimensions(LABEL_X, py, 66, 20).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal(pc.op), b -> {
                            pc.op = next(OPS, pc.op); refresh();
                        }).dimensions(LABEL_X + 72, py, 52, 20).build());
                        TextFieldWidget val = new TextFieldWidget(this.textRenderer, LABEL_X + 130, py, 70, 20, Text.literal("坐标值"));
                        val.setMaxLength(16);
                        val.setText(posValueText);
                        val.setChangedListener(v -> posValueText = v);
                        addDrawableChild(val);
                    }
                }
            }
            case "wait" -> {
                addSection("时长");
                textRow("毫秒", msText, v -> msText = v, null);
            }
            case "command" -> {
                addSection("指令");
                textRow("指令", cmdText, v -> cmdText = v, "自动发送，如 /home");
            }
            case "loop" -> {
                addSection("循环");
                textRow("次数", timesText, v -> timesText = v, null);
                addDrawableChild(ButtonWidget.builder(Text.literal("编辑循环体 (" + st.body.size() + " 子步骤)"), b -> {
                    stepsStack.push(st.body);
                    editingStep = null;
                    refresh();
                }).dimensions(LABEL_X, yOf(nextRow++), 200, 20).build());
            }
            default -> {
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), b -> {
            applyStepFields(st);
            editingStep = null;
            refresh();
        }).dimensions(CONTENT_X, this.height - 34, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> {
            editingStep = null;
            refresh();
        }).dimensions(CONTENT_X + 96, this.height - 34, 90, 20).build());
    }

    /** 循环选择按钮（点击切到下一个选项，不额外加标签行） */
    private void cycleButton(String label, String value, String[] options, Consumer<String> on) {
        if (options == null) return;
        int y = yOf(nextRow++);
        if (!visible(y)) return;
        addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + value), b -> {
            on.accept(next(options, value));
        }).dimensions(LABEL_X, y, 200, 20).build());
    }

    private void applyStepFields(Step st) {
        try {
            if (msText != null && !msText.isEmpty()) st.ms = Math.max(0, Integer.parseInt(msText.trim()));
        } catch (NumberFormatException ignored) {
        }
        try {
            if (timesText != null && !timesText.isEmpty()) st.times = Math.max(1, Integer.parseInt(timesText.trim()));
        } catch (NumberFormatException ignored) {
        }
        st.value = cmdText;
        if (st.cond.isEmpty()) st.cond.add(new PosCond());
        try {
            st.cond.get(0).value = Double.parseDouble(posValueText.trim());
        } catch (NumberFormatException ignored) {
        }
    }

    private static String next(String[] options, String current) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(current)) return options[(i + 1) % options.length];
        }
        return options[0];
    }

    // ==================== 渲染 / 输入 ====================

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawText(this.textRenderer, Text.literal("SkyScript 控制台"), 6, 6, 0xFFFFFF, true);
        for (Object[] item : renderItems) {
            int y = (int) item[0];
            if (y < TOP - 12 || y > this.height - 44) continue;
            String text = (String) item[1];
            int flag = (int) item[2];
            if (flag == 1) {
                ctx.drawText(this.textRenderer, Text.literal(text), LABEL_X, y, 0xFFD67E, true);
            } else if (flag == 2) {
                ctx.drawText(this.textRenderer, Text.literal(text), LABEL_X, y, 0xAAAAAA, false);
            } else {
                ctx.drawText(this.textRenderer, Text.literal(text), LABEL_X, y, 0xFFFFFF, false);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (int) (verticalAmount * 12));
        int max = Math.max(0, nextRow * ROW_H - (this.height - 64));
        if (scroll > max) scroll = max;
        refresh();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (capturing) {
            if (input.isEscape()) {
                capturing = false;
            } else if (editingStep != null) {
                String name = KeyNames.nameOf(input.key());
                if (!editingStep.keys.contains(name)) editingStep.keys.add(name);
                capturing = false;
            }
            refresh();
            return true;
        }
        return super.keyPressed(input);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float parseFloat(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static List<String> parseKeys(String s) {
        List<String> out = new ArrayList<>();
        for (String part : s.split("[,\\s]+")) {
            String k = part.trim().toUpperCase();
            if (!k.isEmpty() && !out.contains(k)) out.add(k);
        }
        return out.isEmpty() ? new ArrayList<>(List.of("A", "D")) : out;
    }
}

package dev.skyscript.screen;

import dev.skyscript.config.Settings;
import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.hud.HudEditor;
import dev.skyscript.input.KeyEvents;
import dev.skyscript.input.KeyNames;
import dev.skyscript.script.PosCond;
import dev.skyscript.script.Script;
import dev.skyscript.script.Step;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

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
    private static final int ZONE_W = 30;
    private static final int ZONE_GAP = 2;

    // 循环选择项：显示值 ↔ 存储值
    private static final String[][] POS = {{"左上", "top-left"}, {"右上", "top-right"}, {"左下", "bottom-left"}, {"右下", "bottom-right"}};
    private static final String[][] SEM = {{"停止", "stop"}, {"忽略", "ignore"}};
    private static final String[][] METH = {{"游戏内注入", "inject"}, {"系统级模拟", "os"}};
    private static final String[][] OPS_MAP = {{"≤", "<="}, {"≥", ">="}, {"<", "<"}, {">", ">"}, {"=", "=="}};
    private static final String[][] UNTIL_MAP = {{"到时间", "time"}, {"到坐标", "position"}, {"手动", "manual"}};
    private static final String[][] POS_OPS = {{"忽略", "ignore"}, {"≤", "<="}, {"≥", ">="}, {"<", "<"}, {">", ">"}, {"=", "=="}};
    private static final String[] STEP_TYPE_ORDER = {"按住按键（长按）", "点按按键", "等待", "发送指令", "循环"};

    private Tab tab;
    private int scroll;
    private int nextRow; // 行号游标（含区块标题行）

    /**
     * 标签控件：把文字当作可绘制控件走控件渲染路径。
     * 实测在 render() 里直接 ctx.drawText 画不出文字，但控件(按钮)能显示文字，
     * 所以所有标签都做成控件来保证可见。
     */
    private static final class LabelDrawable implements Element, Drawable, Selectable {
        private final Text text;
        private final int x, y;
        private final boolean bar; // 是否画深色底（列表行用）

        LabelDrawable(String text, int x, int y, boolean bar) {
            this.text = Text.literal(text);
            this.x = x;
            this.y = y;
            this.bar = bar;
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            Text toDraw = text;
            if (bar) {
                int w = MinecraftClient.getInstance().getWindow().getScaledWidth();
                int barRight = w - 180;
                ctx.fill(x - 4, y - 2, barRight, y + 16, 0x65000000);
                toDraw = truncateToFit(text.getString(), barRight - x - 6);
            }
            // 用立即绘制的 DrawnTextConsumer（按钮同款），不用 ctx.drawText（延迟队列，实测渲染不出来）
            ctx.getTextConsumer().text(x, y, toDraw);
        }

        /** 文字超出可用宽度时截断并加省略号（避免盖到右侧按钮） */
        private static Text truncateToFit(String s, int maxWidth) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            if (tr.getWidth(s) <= maxWidth) return Text.literal(s);
            String cut = s;
            while (cut.length() > 1 && tr.getWidth(cut + "…") > maxWidth) {
                cut = cut.substring(0, cut.length() - 1);
            }
            return Text.literal(cut + "…");
        }

        // ---- Element / Selectable 的 no-op（纯显示控件，不参与交互/焦点/旁白） ----
        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public boolean isFocused() {
            return false;
        }

        @Override
        public Selectable.SelectionType getType() {
            return Selectable.SelectionType.NONE;
        }

        @Override
        public void appendNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        }
    }

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
    /** 按键录制目标：null=未录制 / "step"=步骤按键 / "master"=总控键 / "settings"=控制台键 */
    private String capturingKey;
    // 步骤编辑表单临时文本
    private String msText = "", timesText = "1", cmdText = "";
    // 坐标条件（X/Y/Z 三轴，忽略=不限）
    private String posOpX = "忽略", posValX = "0", posOpY = "忽略", posValY = "0", posOpZ = "忽略", posValZ = "0";

    // ---- 新建方案向导 ----
    private boolean inWizard;    private String wizName = "";
    private String wizStartKey = "A";
    private String wizAxis = "x";
    private String wizOpA = "<=", wizValA = "100";
    private String wizOpD = ">=", wizValD = "200";
    private String wizCols = "5";
    private String wizPause = "0.5";
    private String wizCmd = "/home";
    private String wizRounds = "0";

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

    /** 加一个标签控件（flag: 1=区块标题金 / 2=灰色提示 / 3=深色底列表行 / 0=普通白字） */
    private void rowLabel(String text, int y, int flag) {
        if (!visible(y)) return;
        String colored = switch (flag) {
            case 1 -> "§e§l" + text;
            case 2 -> "§7" + text;
            case 3 -> text;
            default -> text;
        };
        addDrawableChild(new LabelDrawable(colored, LABEL_X, y, flag == 3));
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
        rowLabel(text, y, 1);
    }

    /** 纯文字行（列表项等） */
    private void addLabel(String text, int ignoredColor) {
        int y = yOf(nextRow++);
        rowLabel(text, y, 0);
    }

    private void toggleRow(String label, boolean value, Consumer<Boolean> on) {
        int y = yOf(nextRow++);
        rowLabel(label, y, 0);
        if (!visible(y)) return;
        addDrawableChild(ButtonWidget.builder(Text.literal(value ? "§a开" : "§c关"), b -> on.accept(!value))
                .dimensions(ctrlX(), y, 60, 20).build());
    }

    private void cycleRow(String label, String value, String[][] map, Consumer<String> on) {
        int y = yOf(nextRow++);
        rowLabel(label, y, 0);
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
        rowLabel(label, y, 0);
        if (visible(y)) {
            TextFieldWidget tf = new TextFieldWidget(this.textRenderer, ctrlX(), y, ctrlW(), 20, Text.literal(""));
            tf.setMaxLength(200);
            tf.setText(initial);
            tf.setChangedListener(on);
            addDrawableChild(tf);
        }
        if (hint != null) {
            int hy = yOf(nextRow++);
            rowLabel(hint, hy, 2);
        }
    }

    /** 官方式键位绑定行：点击进入"等待按键"，按任意键直接绑定到对应配置项 */
    private void keyBindRow(String label, String value, String target) {
        int y = yOf(nextRow++);
        rowLabel(label, y, 0);
        if (!visible(y)) return;
        boolean cap = target.equals(capturingKey);
        String btn = cap ? "§e> 按任意键… (ESC 取消)" : (value == null || value.isEmpty() ? "未设置" : value);
        addDrawableChild(ButtonWidget.builder(Text.literal(btn), b -> {
            capturingKey = cap ? null : target;
            if (!cap) drainPressedQueue();
            refresh();
        }).dimensions(ctrlX(), y, 140, 20).build());
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

        nextRow = 0;
        addDrawableChild(new LabelDrawable("§fSkyScript 控制台", 6, 6, false));
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
        addSection("一键收割（F8）");
        addLabel("按 F8 = 全自动开始：脚本 + 连点攻击 + 锁鼠标；再按 = 全部停止还原。", 0xFFFFFF);
        addLabel("脚本不会立即跑，按触发键（默认 A/D）才启动。", 0xFFFFFF);
        addSection("运行");
        toggleRow("进游戏自动开启", mArmedOnJoin, v -> { mArmedOnJoin = v; refresh(); });
        textRow("触发键（点击启动）", triggerKeys, v -> triggerKeys = v, "逗号分隔，如 A, D");
        cycleRow("运行中再按当前方向键", curKeySem, SEM, v -> { curKeySem = v; refresh(); });
        addSection("F8 一起做的事");
        toggleRow("启动 / 停止脚本", mToggleScript, v -> { mToggleScript = v; refresh(); });
        toggleRow("连点攻击模式（官方切换）", mToggleAttack, v -> { mToggleAttack = v; refresh(); });
        toggleRow("显示 / 隐藏 HUD", mToggleHud, v -> { mToggleHud = v; refresh(); });
        textRow("锁鼠标热键（Lunar 等）", extKey, v -> extKey = v, "F8 按下时触发一次，可留空");
        cycleRow("触发方式", extMethod, METH, v -> { extMethod = v; refresh(); });
        toggleRow("聊天反馈", mFeedback, v -> { mFeedback = v; refresh(); });
        addSection("按键");
        keyBindRow("控制台键（打开面板）", settingsKeyName, "settings");
        keyBindRow("总控键（F8 全自动）", masterKeyName, "master");
        addSection("高级");
        toggleRow("方向交换（诊断）", directionSwap, v -> { directionSwap = v; refresh(); });
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
        addDrawableChild(ButtonWidget.builder(Text.literal("拖动调整 HUD 位置…"), b -> {
            saveSettings();
            HudEditor.toggle();
            close();
        }).dimensions(CONTENT_X, this.height - 34, 160, 20).build());
    }

    private void buildScriptsTab() {
        if (inWizard) {
            buildWizard();
        } else if (editingScript == null) {
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
        addLabel("活动: " + activeScriptName(), 0xFFFFFF);

        // 新建
        addDrawableChild(ButtonWidget.builder(Text.literal("新建空方案"), b -> {
            String nm = uniqueName("Preset");
            Script s = new Script(nm); // 空方案，从零加动作
            SkyScriptConfig.saveScript(s);
            SkyScriptConfig.get().activeScript = s.name;
            SkyScriptConfig.save();
            editingScript = s;
            stepsStack.clear();
            stepsStack.push(s.steps);
            editingStep = null;
            scroll = 0;
            refresh();
        }).dimensions(CONTENT_X, this.height - 34, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("农田向导…"), b -> {
            inWizard = true;
            wizName = "";
            scroll = 0;
            refresh();
        }).dimensions(CONTENT_X + 106, this.height - 34, 90, 20).build());

        int zoneStart = this.width - 16 - ZONE_W * 3 - ZONE_GAP * 2;
        for (int i = 0; i < scripts.size(); i++) {
            Script s = scripts.get(i);
            boolean isActive = s.name.equals(activeScriptName());
            // 第 1 行：名字 + 循环 + 操作区
            int y = yOf(nextRow++);
            rowLabel((isActive ? "§a▶ " : "  ") + s.name + "   §7" + (s.loop == 0 ? "无限循环" : "×" + s.loop + " 轮"), y, 3);
            if (visible(y)) {
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
            // 第 2 行：整份方案的人话描述
            int y2 = yOf(nextRow++);
            rowLabel("§7" + s.describe(), y2, 3);
        }
        if (scripts.isEmpty()) {
            addLabel("还没有方案，点「新建空方案」从零开始，或用「农田向导」", 0xFFFFFF);
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

    // ---------- 新建方案向导 ----------

    private void buildWizard() {
        addSection("新建方案（向导）");
        addLabel("填下面几项，自动生成「走到坐标就换方向」的脚本。", 0xFFFFFF);
        textRow("方案名", wizName, v -> wizName = v, "留空自动取名");
        cycleRow("第一列方向（起始键）", wizStartKey, new String[][]{{"A（向左）", "A"}, {"D（向右）", "D"}}, v -> { wizStartKey = v; refresh(); });
        cycleRow("坐标轴", wizAxis, new String[][]{{"X（东西）", "x"}, {"Y（高度）", "y"}, {"Z（南北）", "z"}}, v -> { wizAxis = v; refresh(); });
        addSection("两列各自走到哪");
        addLabel("A 列（起始键那一列）", 0xFFFFFF);
        cycleRow("A 列比较", wizOpA, OPS_MAP, v -> { wizOpA = v; refresh(); });
        textRow("A 列坐标值", wizValA, v -> wizValA = v, null);
        addLabel("D 列（另一键那一列）", 0xFFFFFF);
        cycleRow("D 列比较", wizOpD, OPS_MAP, v -> { wizOpD = v; refresh(); });
        textRow("D 列坐标值", wizValD, v -> wizValD = v, null);
        addSection("一趟怎么走");
        textRow("每趟列数（交替几次）", wizCols, v -> wizCols = v, "如 5 = A,D,A,D,A 共 5 段");
        textRow("列间暂停（秒）", wizPause, v -> wizPause = v, "如 0.5");
        textRow("每趟结束指令", wizCmd, v -> wizCmd = v, "如 /home，可留空");
        textRow("整趟循环次数（0=无限）", wizRounds, v -> wizRounds = v, "每跑完一趟（含指令）算一轮");
        addDrawableChild(ButtonWidget.builder(Text.literal("生成方案"), b -> generateFromWizard())
                .dimensions(CONTENT_X, this.height - 34, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), b -> { inWizard = false; refresh(); })
                .dimensions(CONTENT_X + 96, this.height - 34, 70, 20).build());
    }

    /** 按向导字段生成脚本并落盘，设为活动方案 */
    private void generateFromWizard() {
        refreshScripts();
        String nm = wizName == null ? "" : wizName.trim();
        if (nm.isEmpty()) nm = uniqueName("新方案");
        Script s = new Script(nm);
        s.loop = Math.max(0, parseInt(wizRounds, 0));
        int cols = Math.max(1, parseInt(wizCols, 1));
        int pauseMs = (int) Math.round(Math.max(0, parseDouble(wizPause, 0.5)) * 1000);
        String axis = wizAxis == null || wizAxis.isEmpty() ? "x" : wizAxis;
        PosCond ca = new PosCond(axis, wizOpA == null ? "<=" : wizOpA, parseDouble(wizValA, 100));
        PosCond cd = new PosCond(axis, wizOpD == null ? ">=" : wizOpD, parseDouble(wizValD, 200));
        String key = "D".equals(wizStartKey) ? "D" : "A";
        String other = "A".equals(key) ? "D" : "A";
        for (int i = 0; i < cols; i++) {
            boolean firstSeg = i % 2 == 0;
            Step h = new Step();
            h.type = "hold";
            h.keys.add(firstSeg ? key : other);
            h.untilType = "position";
            h.cond.add(firstSeg ? ca.copy() : cd.copy());
            s.steps.add(h);
            Step w = new Step();
            w.type = "wait";
            w.ms = pauseMs;
            s.steps.add(w);
        }
        if (wizCmd != null && !wizCmd.trim().isEmpty()) {
            Step c = new Step();
            c.type = "command";
            c.value = wizCmd.trim();
            s.steps.add(c);
        }
        SkyScriptConfig.saveScript(s);
        SkyScriptConfig.get().activeScript = s.name;
        SkyScriptConfig.save();
        inWizard = false;
        refresh();
    }

    // ---------- 步骤列表 ----------

    private void buildStepList() {
        if (editingScript == null) return;
        List<Step> steps = stepsStack.peek();
        if (steps == null) return;
        String title = stepsTitle();

        addSection(title);
        // 添加动作 / 返回
        addDrawableChild(ButtonWidget.builder(Text.literal("添加动作"), b -> {
            Step step = new Step();
            editingStep = step;
            steps.add(step);
            msText = "";
            timesText = "1";
            cmdText = "";
            resetPosForm();
            capturingKey = null;
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
            rowLabel((i + 1) + ". " + st.summary(), y, 3);
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
            addLabel("还没有动作，点「添加动作」开始", 0xFFFFFF);
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
        msText = formatSeconds(st.ms);
        timesText = st.times > 0 ? String.valueOf(st.times) : "1";
        cmdText = st.value == null ? "" : st.value;
        loadPosForm(st.cond);
        capturingKey = null;
        scroll = 0;
        refresh();
    }

    // ---------- 步骤编辑 ----------

    private void buildStepEdit() {
        if (editingStep == null) return;
        Step st = editingStep;
        addSection("编辑动作");
        addLabel("点击顶部「动作」切换类型：按住=长按移动 · 点按=按一下 · 等待 · 发指令 · 循环", 2);
        addStepTypeButton(st);

        switch (st.type) {
            case "hold", "press" -> {
                addSection("按键");
                // MC 原生式绑键按钮：点击进入"等待按键"，按任意键直接绑定，反馈就在按钮上
                boolean cap = "step".equals(capturingKey);
                String keyText = cap
                        ? "§e> 按任意键… (ESC 取消)"
                        : (st.keys.isEmpty() ? "按键：未设置" : "按键：" + String.join(" + ", st.keys));
                addDrawableChild(ButtonWidget.builder(Text.literal(keyText), b -> {
                    capturingKey = cap ? null : "step";
                    if (!cap) drainPressedQueue(); // 清掉进入录制前的残留按键，避免闪一下就绑定
                    refresh();
                }).dimensions(LABEL_X, yOf(nextRow++), 270, 20).build());
                if (!st.keys.isEmpty()) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("清除"), b -> { st.keys.clear(); refresh(); })
                            .dimensions(LABEL_X + 276, yOf(nextRow - 1), 60, 20).build());
                }
                // 只有"长按"才需要结束条件；点按是一次性按下
                if ("hold".equals(st.type) || "hold".equals(st.mode)) {
                    addUntilButton(st);
                    if ("time".equals(st.untilType)) {
                        textRow("时长（秒）", msText, v -> msText = v, "按住多久，如 120 / 0.5");
                    } else if ("position".equals(st.untilType)) {
                        addSection("走到坐标（忽略=不限该轴）");
                        posAxisRow("X", posOpX, posValX, v -> posOpX = v, v -> posValX = v);
                        posAxisRow("Y", posOpY, posValY, v -> posOpY = v, v -> posValY = v);
                        posAxisRow("Z", posOpZ, posValZ, v -> posOpZ = v, v -> posValZ = v);
                    }
                }
            }
            case "wait" -> {
                addSection("时长");
                textRow("时长（秒）", msText, v -> msText = v, "等待多久");
            }
            case "command" -> {
                addSection("指令");
                textRow("指令", cmdText, v -> cmdText = v, "自动发送，如 /home");
            }
            case "loop" -> {
                addSection("循环");
                textRow("重复次数", timesText, v -> timesText = v, null);
                addDrawableChild(ButtonWidget.builder(Text.literal("编辑循环体（" + st.body.size() + " 步）"), b -> {
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

    /** 动作类型选择按钮（大白话 → 底层 type/mode） */
    private void addStepTypeButton(Step st) {
        int y = yOf(nextRow++);
        if (!visible(y)) return;
        String current = stepTypeLabel(st);
        addDrawableChild(ButtonWidget.builder(Text.literal("动作: " + current), b -> {
            applyStepType(st, next(STEP_TYPE_ORDER, current));
            refresh();
        }).dimensions(LABEL_X, y, 240, 20).build());
    }

    private static String stepTypeLabel(Step st) {
        return switch (st.type) {
            case "hold" -> "按住按键（长按）";
            case "press" -> "tap".equals(st.mode) ? "点按按键" : "按住按键（长按）";
            case "wait" -> "等待";
            case "command" -> "发送指令";
            case "loop" -> "循环";
            default -> st.type;
        };
    }

    private static void applyStepType(Step st, String label) {
        switch (label) {
            case "点按按键" -> { st.type = "press"; st.mode = "tap"; }
            case "等待" -> st.type = "wait";
            case "发送指令" -> st.type = "command";
            case "循环" -> st.type = "loop";
            default -> st.type = "hold"; // 按住按键（长按）
        }
    }

    /** 结束条件选择按钮（到时间 / 到坐标 / 手动） */
    private void addUntilButton(Step st) {
        int y = yOf(nextRow++);
        if (!visible(y)) return;
        String current = untilLabel(st.untilType);
        addDrawableChild(ButtonWidget.builder(Text.literal("结束: " + current), b -> {
            String nextDisplay = next(displayOf(UNTIL_MAP), current);
            st.untilType = storeOf(UNTIL_MAP, nextDisplay);
            refresh();
        }).dimensions(LABEL_X, y, 240, 20).build());
    }

    private static String untilLabel(String ut) {
        return switch (ut == null ? "time" : ut) {
            case "position" -> "到坐标";
            case "manual" -> "手动";
            default -> "到时间";
        };
    }

    private static String[] displayOf(String[][] map) {
        String[] out = new String[map.length];
        for (int i = 0; i < map.length; i++) out[i] = map[i][0];
        return out;
    }

    private static String storeOf(String[][] map, String display) {
        for (String[] p : map) if (p[0].equals(display)) return p[1];
        return map[0][1];
    }

    private void applyStepFields(Step st) {
        try {
            if (msText != null && !msText.isEmpty()) {
                st.ms = (int) Math.round(Math.max(0, Double.parseDouble(msText.trim())) * 1000);
            }
        } catch (NumberFormatException ignored) {
        }
        try {
            if (timesText != null && !timesText.isEmpty()) st.times = Math.max(1, Integer.parseInt(timesText.trim()));
        } catch (NumberFormatException ignored) {
        }
        st.value = cmdText;
        // 坐标条件：按 X/Y/Z 重建（"忽略"的轴不加入）
        List<PosCond> conds = new ArrayList<>();
        addAxisCond(conds, "x", posOpX, posValX);
        addAxisCond(conds, "y", posOpY, posValY);
        addAxisCond(conds, "z", posOpZ, posValZ);
        st.cond = conds;
    }

    private static void addAxisCond(List<PosCond> conds, String axis, String op, String val) {
        if (op == null || "ignore".equals(op)) return;
        try {
            conds.add(new PosCond(axis, op, Double.parseDouble(val.trim())));
        } catch (Exception ignored) {
        }
    }

    /** 打开表单时从步骤现有坐标条件填充 X/Y/Z */
    private void loadPosForm(List<PosCond> conds) {
        resetPosForm();
        if (conds != null) {
            for (PosCond pc : conds) {
                if (pc == null) continue;
                switch (pc.axis == null ? "x" : pc.axis) {
                    case "y" -> { posOpY = pc.op; posValY = fmtNum(pc.value); }
                    case "z" -> { posOpZ = pc.op; posValZ = fmtNum(pc.value); }
                    default -> { posOpX = pc.op; posValX = fmtNum(pc.value); }
                }
            }
        }
    }

    private void resetPosForm() {
        posOpX = "忽略"; posValX = "0";
        posOpY = "忽略"; posValY = "0";
        posOpZ = "忽略"; posValZ = "0";
    }

    private static String fmtNum(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    /** 一行坐标轴：轴名 + 比较符按钮 + 值输入框 */
    private void posAxisRow(String axis, String op, String val, Consumer<String> onOp, Consumer<String> onVal) {
        int y = yOf(nextRow++);
        rowLabel(axis, y, 0);
        if (!visible(y)) return;
        String[] displays = displayOf(POS_OPS);
        String cur = curDisplay(POS_OPS, op);
        addDrawableChild(ButtonWidget.builder(Text.literal(cur), b -> {
            onOp.accept(storeOf(POS_OPS, next(displays, cur)));
            refresh();
        }).dimensions(LABEL_X + 18, y, 62, 20).build());
        TextFieldWidget tf = new TextFieldWidget(this.textRenderer, LABEL_X + 86, y, 100, 20, Text.literal(axis + " 坐标值"));
        tf.setMaxLength(16);
        tf.setText(val);
        tf.setChangedListener(onVal);
        addDrawableChild(tf);
    }

    private static String curDisplay(String[][] map, String store) {
        for (String[] p : map) if (p[1].equals(store)) return p[0];
        return map[0][0];
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
        // 所有文字都走 LabelDrawable 控件（在 super.render 里随控件一起画），
        // 不再在此手绘 drawText（实测手绘文字渲染不出来）。
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (int) (verticalAmount * 12));
        int max = Math.max(0, nextRow * ROW_H - (this.height - 64));
        if (scroll > max) scroll = max;
        refresh();
        return true;
    }

    /**
     * 每 tick 检查"录制按键"：从 KeyEvents（KeyEventCatcher mixin 记录的真实按键）读取，
     * 不依赖 Screen.keyPressed（后者可能被输入框焦点吃掉）。
     */
    @Override
    public void tick() {
        super.tick();
        if (capturingKey != null) {
            Integer kc = KeyEvents.pollPressed();
            if (kc != null) {
                handleCapturedKey(kc);
            }
        }
    }

    private void handleCapturedKey(int kc) {
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            capturingKey = null;
            refresh();
            return;
        }
        String name = KeyNames.nameOf(kc);
        if ("step".equals(capturingKey)) {
            if (editingStep != null && !editingStep.keys.contains(name)) editingStep.keys.add(name);
        } else if ("master".equals(capturingKey)) {
            masterKeyName = name;
        } else if ("settings".equals(capturingKey)) {
            settingsKeyName = name;
        }
        capturingKey = null;
        refresh();
    }

    /** 清空按键队列（进入录制前调用，避免残留按键被立即绑定） */
    private void drainPressedQueue() {
        while (KeyEvents.pollPressed() != null) {
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // 录制期间吞掉所有按键（由 tick 处理），防止 ESC 顺手关掉面板 / 键进输入框
        if (capturingKey != null) return true;
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

    private static double parseDouble(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 毫秒 → 秒字符串（去掉多余的 .0），如 120000→"120"、500→"0.5" */
    private static String formatSeconds(int ms) {
        if (ms <= 0) return "";
        double s = ms / 1000.0;
        if (s == (long) s) return String.valueOf((long) s);
        return String.valueOf(s);
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

package dev.skyscript.screen;

import dev.skyscript.config.Settings;
import dev.skyscript.config.SkyScriptConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * SkyScript 设置界面（/skyscript 或 Mod Menu 打开）。
 * 所有项先改内存副本，点「保存并返回」才写盘；「恢复默认」重置为出厂值。
 */
public class SettingsScreen extends Screen {

    private static final int ROW_H = 20;
    private static final int CTRL_X = 130;
    private static final String[] POSITIONS = {"top-left", "top-right", "bottom-left", "bottom-right"};
    private static final String[] KEY_SEMS = {"ignore", "stop"};
    private static final String[] METHODS = {"inject", "os"};

    private static final String[] LABELS = {
            "HUD 显示", "静默模式", "HUD 位置", "位置 X", "位置 Y", "缩放", "背景", "HUD 模板",
            "运行中按当前键", "触发键 (逗号分隔)", "总控键", "编辑器键", "设置键",
            "联动·脚本", "联动·攻击模式", "联动·HUD", "外部热键", "外部热键方式", "消息反馈"
    };

    private int scroll;

    // 编辑副本
    private boolean hudEnabled, hudSilent, hudBackground;
    private String hudPos, hudX, hudY, hudScale, hudTemplate;
    private String curKeySem, triggerKeys, masterKeyName, editorKeyName, settingsKeyName;
    private boolean mToggleScript, mToggleAttack, mToggleHud, mFeedback;
    private String extKey, extMethod;

    public SettingsScreen() {
        super(Text.literal("SkyScript 设置"));
        loadFromConfig();
    }

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
        editorKeyName = s.editorKeyName;
        settingsKeyName = s.settingsKeyName;
        mToggleScript = s.master.toggleScript;
        mToggleAttack = s.master.toggleAttackMode;
        mToggleHud = s.master.toggleHud;
        mFeedback = s.master.feedback;
        extKey = s.master.externalKeys.isEmpty() ? "" : s.master.externalKeys.get(0).key;
        extMethod = s.master.externalKeys.isEmpty() ? "inject" : s.master.externalKeys.get(0).method;
    }

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;

        addDrawableChild(ButtonWidget.builder(Text.literal("保存并返回"), b -> {
            save();
            close();
        }).dimensions(w - 290, h - 30, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("恢复默认"), b -> {
            SkyScriptConfig.get().resetToDefaults();
            loadFromConfig();
            refresh();
        }).dimensions(w - 194, h - 30, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close())
                .dimensions(w - 98, h - 30, 80, 20).build());

        int ctrlW = w - CTRL_X - 16;
        for (int i = 0; i < LABELS.length; i++) {
            int y = 24 + i * ROW_H - scroll;
            if (y < 20 || y > h - 34) continue;
            switch (i) {
                case 0 -> addToggle(y, hudEnabled, v -> { hudEnabled = v; refresh(); });
                case 1 -> addToggle(y, hudSilent, v -> { hudSilent = v; refresh(); });
                case 2 -> addCycle(y, hudPos, POSITIONS, v -> { hudPos = v; refresh(); });
                case 3 -> addText(y, ctrlW, hudX, v -> hudX = v);
                case 4 -> addText(y, ctrlW, hudY, v -> hudY = v);
                case 5 -> addText(y, ctrlW, hudScale, v -> hudScale = v);
                case 6 -> addToggle(y, hudBackground, v -> { hudBackground = v; refresh(); });
                case 7 -> addText(y, ctrlW, hudTemplate, v -> hudTemplate = v);
                case 8 -> addCycle(y, curKeySem, KEY_SEMS, v -> { curKeySem = v; refresh(); });
                case 9 -> addText(y, ctrlW, triggerKeys, v -> triggerKeys = v);
                case 10 -> addText(y, ctrlW, masterKeyName, v -> masterKeyName = v);
                case 11 -> addText(y, ctrlW, editorKeyName, v -> editorKeyName = v);
                case 12 -> addText(y, ctrlW, settingsKeyName, v -> settingsKeyName = v);
                case 13 -> addToggle(y, mToggleScript, v -> { mToggleScript = v; refresh(); });
                case 14 -> addToggle(y, mToggleAttack, v -> { mToggleAttack = v; refresh(); });
                case 15 -> addToggle(y, mToggleHud, v -> { mToggleHud = v; refresh(); });
                case 16 -> addText(y, ctrlW, extKey, v -> extKey = v);
                case 17 -> addCycle(y, extMethod, METHODS, v -> { extMethod = v; refresh(); });
                case 18 -> addToggle(y, mFeedback, v -> { mFeedback = v; refresh(); });
                default -> {
                }
            }
        }
        int max = Math.max(0, LABELS.length * ROW_H - (h - 60));
        if (scroll > max) scroll = max;
    }

    private void refresh() {
        clearChildren();
        init();
    }

    private void addToggle(int y, boolean value, Consumer<Boolean> on) {
        addDrawableChild(ButtonWidget.builder(Text.literal(value ? "§a开" : "§c关"), b -> on.accept(!value))
                .dimensions(CTRL_X, y, 60, 20).build());
    }

    private void addCycle(int y, String current, String[] options, Consumer<String> on) {
        addDrawableChild(ButtonWidget.builder(Text.literal(current), b -> {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(current)) {
                    on.accept(options[(i + 1) % options.length]);
                    return;
                }
            }
            on.accept(options[0]);
        }).dimensions(CTRL_X, y, 120, 20).build());
    }

    private void addText(int y, int width, String initial, Consumer<String> on) {
        TextFieldWidget tf = new TextFieldWidget(this.textRenderer, CTRL_X, y, width, 20, Text.literal(""));
        tf.setMaxLength(200);
        tf.setText(initial);
        tf.setChangedListener(on);
        addDrawableChild(tf);
    }

    private void save() {
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
        s.editorKeyName = editorKeyName.trim().toUpperCase();
        s.settingsKeyName = settingsKeyName.trim().toUpperCase();
        s.master.toggleScript = mToggleScript;
        s.master.toggleAttackMode = mToggleAttack;
        s.master.toggleHud = mToggleHud;
        s.master.feedback = mFeedback;
        if (s.master.externalKeys.isEmpty()) s.master.externalKeys.add(new Settings.MasterSettings.ExternalKey());
        s.master.externalKeys.get(0).key = extKey.trim().toUpperCase();
        s.master.externalKeys.get(0).method = extMethod;
        SkyScriptConfig.save();
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

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        for (int i = 0; i < LABELS.length; i++) {
            int y = 24 + i * ROW_H - scroll;
            if (y < 20 || y > this.height - 34) continue;
            ctx.drawText(this.textRenderer, Text.literal("§7" + LABELS[i]), 12, y + 4, 0xFFFFFF, false);
        }
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§7/skyscript editor 打开方案编辑 · H 键同样可以 · 改完点「保存并返回」生效"),
                this.width / 2, this.height - 52, 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (int) (verticalAmount * 10));
        int max = Math.max(0, LABELS.length * ROW_H - (this.height - 60));
        if (scroll > max) scroll = max;
        refresh();
        return true;
    }
}

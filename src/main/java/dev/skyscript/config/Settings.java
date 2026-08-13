package dev.skyscript.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局设置（config/sky_script/settings.json）。
 */
public class Settings {

    /** 运行中按当前方向键：stop=停止 / ignore=无操作 */
    public String currentKeySemantics = "ignore";

    /** 运行中按另一个方向键：switch=切换方向 */
    public String otherKeySemantics = "switch";

    /** 空闲时按下可启动脚本的键（抬起触发） */
    public List<String> triggerKeys = new ArrayList<>(List.of("A", "D"));

    /** 活动方案名（F8 启动 / 触发键启动时运行它） */
    public String activeScript = "";

    public HudSettings hud = new HudSettings();
    public MasterSettings master = new MasterSettings();

    public static class HudSettings {
        public boolean enabled = true;
        /** 静默模式：完全不渲染、不弹任何提示 */
        public boolean silent = false;
        public String template = "SkyScript §7{state} §f{script} §7{step} §f{timeLeft}s §7{attackMode}";
        /** top-left / top-right / bottom-left / bottom-right */
        public String pos = "top-left";
        public int x = 4;
        public int y = 4;
        public boolean background = true;
        public float scale = 1.0f;
    }

    public static class MasterSettings {
        /** F8 是否启停脚本 */
        public boolean toggleScript = true;
        /** F8 是否切换攻击/摧毁模式（配合左键锁定） */
        public boolean toggleAttackMode = true;
        /** F8 是否切换 HUD 显示 */
        public boolean toggleHud = true;
        /** F8 按下时额外触发的按键（如 Lunar/SkyHanni 的锁定鼠标热键） */
        public List<ExternalKey> externalKeys = new ArrayList<>();

        public static class ExternalKey {
            public String key = "";
            /** inject=游戏内事件注入 / os=OS 级模拟 */
            public String method = "inject";

            public ExternalKey() {
            }

            public ExternalKey(String key, String method) {
                this.key = key;
                this.method = method;
            }
        }
    }

    /** 反序列化后补齐默认值（JSON 里缺失的字段会是 null） */
    public void applyDefaults() {
        if (currentKeySemantics == null) currentKeySemantics = "ignore";
        if (otherKeySemantics == null) otherKeySemantics = "switch";
        if (triggerKeys == null) triggerKeys = new ArrayList<>(List.of("A", "D"));
        if (activeScript == null) activeScript = "";
        if (hud == null) hud = new HudSettings();
        if (hud.template == null) hud.template = "SkyScript §7{state} §f{script} §7{step} §f{timeLeft}s §7{attackMode}";
        if (hud.pos == null) hud.pos = "top-left";
        if (master == null) master = new MasterSettings();
        if (master.externalKeys == null) master.externalKeys = new ArrayList<>();
        if (master.externalKeys.isEmpty()) master.externalKeys.add(new MasterSettings.ExternalKey("PGDN", "inject"));
    }
}

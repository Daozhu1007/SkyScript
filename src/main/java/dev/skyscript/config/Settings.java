package dev.skyscript.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局设置（config/sky_script/settings.json）。
 */
public class Settings {

    /** 配置文件版本：<2 视为旧版，加载时迁移（见 applyDefaults） */
    public int version = 0;

    /** 运行中按当前方向键：stop=停止 / ignore=无操作（默认 stop，与原 AHK 一致） */
    public String currentKeySemantics = "stop";

    /** 运行中按另一个方向键：switch=切换方向 */
    public String otherKeySemantics = "switch";

    /** 触发键（点击启动）；空列表 = 自动用"活动方案第一个动作的按键" */
    public List<String> triggerKeys = new ArrayList<>();

    /** 活动方案名（F8 启动 / 触发键启动时运行它） */
    public String activeScript = "";

    /** 总控键（默认 F8），轮询检测，可在设置里改名 */
    public String masterKeyName = "F8";

    /** 控制台键（默认 O），轮询检测，打开整合控制台，可在设置里改名 */
    public String settingsKeyName = "O";

    /** 方向交换（诊断用兜底）：true 时 A/D 注入方向翻转 */
    public boolean directionSwap = false;

    /** 调试日志：聊天栏打印脚本执行全过程（步骤切换/坐标误差/按键状态/停止原因），排查问题时开启 */
    public boolean debugMode = false;

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
        /** F8 是否控制脚本总开关（arm/disarm，见 ScriptEngine.isArmed） */
        public boolean toggleScript = true;
        /** F8 开启时是否直接启动活动方案（而不是只武装等触发键） */
        public boolean startOnArm = false;
        /** F8 是否切换攻击/摧毁模式（配合左键锁定） */
        public boolean toggleAttackMode = true;
        /** F8 联动 HUD：开启联动后，F8 开启时显示 HUD、关闭时隐藏（默认关，HUD 主开关在设置界面） */
        public boolean toggleHud = false;
        /** 启动/停止时是否发送聊天反馈消息 */
        public boolean feedback = true;
        /** 进游戏/进世界时是否默认处于"已开启"状态（默认关，与原 AHK 的 F8 总开关一致） */
        public boolean armedOnJoin = false;
        /** 总控键按下时额外触发的按键（如 Lunar/SkyHanni 的锁定鼠标热键） */
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

    /** 反序列化后补齐默认值（JSON 里缺失的字段会是 null），并做版本迁移 */
    public void applyDefaults() {
        if (currentKeySemantics == null) currentKeySemantics = "stop";
        if (otherKeySemantics == null) otherKeySemantics = "switch";
        if (triggerKeys == null) triggerKeys = new ArrayList<>();
        if (activeScript == null) activeScript = "";
        if (masterKeyName == null) masterKeyName = "F8";
        if (settingsKeyName == null) settingsKeyName = "O";
        // directionSwap 是 boolean，无 null 问题，保持默认
        if (hud == null) hud = new HudSettings();
        if (hud.template == null) hud.template = "SkyScript §7{state} §f{script} §7{step} §f{timeLeft}s §7{attackMode}";
        if (hud.pos == null) hud.pos = "top-left";
        if (master == null) master = new MasterSettings();
        if (master.externalKeys == null) master.externalKeys = new ArrayList<>();
        if (master.externalKeys.isEmpty()) master.externalKeys.add(new MasterSettings.ExternalKey("PGDN", "inject"));
        // v2 迁移：
        //  1) 旧版本把 HUD 开关错误地绑到了 F8（toggleHud=true 残留），一律清理；
        //  2) 旧默认 currentKeySemantics=ignore 偏离了 AHK"再按当前键=停止"的语义，回正为 stop。
        // 迁移后保留用户后续在设置界面里的显式选择。
        if (version < 2) {
            master.toggleHud = false;
            if ("ignore".equals(currentKeySemantics)) currentKeySemantics = "stop";
            version = 2;
        }
        // v3 迁移：触发键默认改为"自动=活动方案里用到的按键"，清掉旧版写死的 A/D
        if (version < 3) {
            if (triggerKeys != null && triggerKeys.equals(List.of("A", "D"))) {
                triggerKeys = new ArrayList<>();
            }
            version = 3;
        }
        // v4：再次兜底清掉写死的 A/D（防止旧配置已到 v3 但仍是 A/D）
        if (version < 4) {
            if (triggerKeys != null && triggerKeys.equals(List.of("A", "D"))) {
                triggerKeys = new ArrayList<>();
            }
            version = 4;
        }
    }

    /** 恢复出厂默认（仅内存，点保存后落盘） */
    public void resetToDefaults() {
        version = 4;
        currentKeySemantics = "stop";
        otherKeySemantics = "switch";
        triggerKeys = new ArrayList<>();
        activeScript = "";
        masterKeyName = "F8";
        settingsKeyName = "O";
        directionSwap = false;
        hud = new HudSettings();
        master = new MasterSettings();
        master.externalKeys.add(new MasterSettings.ExternalKey("PGDN", "inject"));
    }
}

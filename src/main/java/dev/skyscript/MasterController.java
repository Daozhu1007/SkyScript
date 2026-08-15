package dev.skyscript;

import dev.skyscript.config.Settings;
import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.engine.ScriptEngine;
import dev.skyscript.input.KeyNames;
import dev.skyscript.input.KeySimulator;
import dev.skyscript.input.OsKeySimulator;
import dev.skyscript.script.Script;
import net.minecraft.client.MinecraftClient;

/**
 * F8 总控：一键执行联动动作序列（每项可在设置里开关）——
 * 1. 脚本启停  2. 攻击/摧毁模式切换（配合左键锁定/解锁）  3. 外部热键触发（如锁定鼠标）  4. HUD 开关
 * 启动/停止都会发送聊天反馈（可在设置关闭）。
 */
public final class MasterController {

    private MasterController() {
    }

    public static void onMasterPressed() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null) return;
        Settings.MasterSettings m = SkyScriptConfig.get().master;
        boolean wasArmed = ScriptEngine.INSTANCE.isArmed();
        boolean screenOpen = c.currentScreen != null;

        // 1. 脚本总开关（arm/disarm）：开启只"武装"，脚本等触发键才启动；若开 startOnArm 则直接启动。
        if (m.toggleScript) {
            if (!wasArmed) {
                ScriptEngine.INSTANCE.setArmed(true);
                // 清掉武装前残留的 A/D 点击事件，避免"按 F8 自己就开始跑"
                ScriptEngine.INSTANCE.resetTriggers();
                Script s = SkyScriptConfig.getActiveScript();
                if (m.startOnArm) {
                    if (s != null) {
                        ScriptEngine.INSTANCE.start(s);
                        Feedback.notify("§a[SkyScript] §f全自动已开启: §e" + s.name);
                    } else {
                        Feedback.notify("§6[SkyScript] §f没有活动方案，按 §eO§f 设置");
                    }
                } else {
                    Feedback.notify(s == null
                            ? "§a[SkyScript] §f已开启: 按触发键开始"
                            : "§a[SkyScript] §f已开启: 按触发键开始 §7[" + s.name + "]");
                }
            } else {
                ScriptEngine.INSTANCE.setArmed(false); // 内部会 stop()
                Feedback.notify("§c[SkyScript] §f已关闭: 触发键不再响应");
            }
        }

        // 2. 攻击/摧毁模式：开启时切 toggle 并点锁；关闭时解锁并回 hold。
        if (m.toggleAttackMode && !screenOpen) {
            if (!wasArmed) {
                if (AttackModeHelper.setToggle(true)) {
                    KeySimulator.tapMouseLeft(); // 切换模式下点一下锁定持续攻击
                } else {
                    Feedback.notify("§6[SkyScript] §f未找到攻击/摧毁选项（当前版本可能不支持）");
                }
            } else {
                if (AttackModeHelper.isToggle()) {
                    KeySimulator.tapMouseLeft(); // 先点一下解锁
                    AttackModeHelper.setToggle(false);
                }
            }
        }

        // 3. 外部热键（开/关各触发一次，用于切换型功能）
        for (Settings.MasterSettings.ExternalKey ek : m.externalKeys) {
            if (ek == null || ek.key == null || ek.key.isEmpty()) continue;
            Integer code = KeyNames.glfwOf(ek.key);
            if (code == null) continue;
            if ("os".equalsIgnoreCase(ek.method)) {
                OsKeySimulator.tap(code);
            } else if (!screenOpen) {
                KeySimulator.tapKey(code);
            }
        }

        // 4. HUD 联动：开启联动后，F8 开启 → 显示 HUD，F8 关闭 → 隐藏 HUD。
        if (m.toggleHud) {
            SkyScriptConfig.get().hud.enabled = !wasArmed;
            SkyScriptConfig.save();
            Feedback.notify(SkyScriptConfig.get().hud.enabled
                    ? "§7[SkyScript] §fHUD 显示: §a开"
                    : "§7[SkyScript] §fHUD 显示: §c关");
        }
    }
}

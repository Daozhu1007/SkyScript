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
 * F8 总控：一键执行联动动作序列（每项可在 settings.json 里开关）——
 * 1. 脚本启停  2. 攻击/摧毁模式切换（配合左键锁定/解锁）  3. 外部热键触发（如锁定鼠标）  4. HUD 开关
 */
public final class MasterController {

    private MasterController() {
    }

    public static void onMasterPressed() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null) return;
        Settings.MasterSettings m = SkyScriptConfig.get().master;
        boolean wasRunning = ScriptEngine.INSTANCE.isRunning();
        boolean screenOpen = c.currentScreen != null;

        // 1. 脚本启停
        if (m.toggleScript) {
            if (wasRunning) {
                ScriptEngine.INSTANCE.stop();
            } else {
                Script s = SkyScriptConfig.getActiveScript();
                if (s != null) ScriptEngine.INSTANCE.start(s);
            }
        }

        // 2. 攻击/摧毁模式
        if (m.toggleAttackMode && !screenOpen) {
            if (!wasRunning) {
                if (AttackModeHelper.setToggle(true)) {
                    KeySimulator.tapMouseLeft(); // 切换模式下点一下锁定持续攻击
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

        // 4. HUD 开关
        if (m.toggleHud) {
            SkyScriptConfig.get().hud.enabled = !SkyScriptConfig.get().hud.enabled;
            SkyScriptConfig.save();
        }
    }
}

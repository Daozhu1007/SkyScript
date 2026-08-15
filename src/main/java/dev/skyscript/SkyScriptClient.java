package dev.skyscript;

import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.engine.ScriptEngine;
import dev.skyscript.hud.ScriptHud;
import dev.skyscript.input.KeyNames;
import dev.skyscript.screen.HudEditScreen;
import dev.skyscript.screen.SkyScriptScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class SkyScriptClient implements ClientModInitializer {

    private static final KeyBinding.Category CATEGORY =
            new KeyBinding.Category(Identifier.of("sky_script", "category"));

    public static KeyBinding masterKey;
    public static KeyBinding settingsKey;

    /** 轮询按下沿检测用的历史状态（键名 → 上一帧是否按下） */
    private static final Map<String, Boolean> prevKeyStates = new HashMap<>();

    /**
     * 命令里延迟打开界面的标志：/skyscript 执行时聊天框还在关闭流程里，
     * 同步 setScreen 会被聊天框的 close() 顶掉（设置打不开），所以推迟到下一 tick 再开。
     */
    private static SkyScriptScreen.Tab openPanelTab;
    /** /skyscript hud 延迟打开 HUD 编辑屏幕 */
    private static boolean openHudEdit;

    @Override
    public void onInitializeClient() {
        SkyScriptConfig.load();
        registerKeyBindings();
        registerCommands();

        ClientTickEvents.END_CLIENT_TICK.register(SkyScriptClient::onTick);
        HudRenderCallback.EVENT.register((ctx, tc) -> ScriptHud.render(ctx));
        // 断线/退出：停止引擎
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ScriptEngine.INSTANCE.stop();
            ScriptEngine.INSTANCE.resetTriggers();
        });
        // 进入世界：重置所有按键历史状态，防止跨世界残留导致误触发；
        // 并按「进游戏自动开启」配置设置 F8 总开关状态（默认关闭）。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ScriptEngine.INSTANCE.stop();
            ScriptEngine.INSTANCE.resetTriggers();
            ScriptEngine.INSTANCE.setArmed(SkyScriptConfig.get().master.armedOnJoin);
            prevKeyStates.clear();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ScriptEngine.INSTANCE.stop();
            ScriptEngine.INSTANCE.resetTriggers();
        });
    }

    private static void registerKeyBindings() {
        String master = SkyScriptConfig.get().masterKeyName;
        String settings = SkyScriptConfig.get().settingsKeyName;
        masterKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sky_script.master", InputUtil.Type.KEYSYM, keyOf(master, GLFW.GLFW_KEY_F8), CATEGORY));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sky_script.open_settings", InputUtil.Type.KEYSYM, keyOf(settings, GLFW.GLFW_KEY_O), CATEGORY));
    }

    private static int keyOf(String name, int fallback) {
        Integer code = KeyNames.glfwOf(name);
        return code == null ? fallback : code;
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("skyscript")
                    .executes(ctx -> { // /skyscript → 控制台
                        Feedback.notify("§7[SkyScript] §f打开控制台…（也可按 §eO§f）");
                        openPanelTab = SkyScriptScreen.Tab.MAIN;
                        return 1;
                    })
                    .then(literal("editor").executes(ctx -> { // /skyscript editor → 脚本页
                        Feedback.notify("§7[SkyScript] §f打开控制台·脚本…（也可按 §eO§f 后选「脚本」）");
                        openPanelTab = SkyScriptScreen.Tab.SCRIPTS;
                        return 1;
                    }))
                    .then(literal("hud").executes(ctx -> { // /skyscript hud → HUD 位置编辑屏幕
                        Feedback.notify("§a[SkyScript] §f打开 HUD 位置编辑…");
                        openHudEdit = true;
                        return 1;
                    }))
                    .then(literal("help").executes(ctx -> {
                        Feedback.notify("§a[SkyScript] §f命令: §e/skyscript§f 控制台 · §e/skyscript editor§f 脚本页 · §e/skyscript hud§f HUD位置 · 快捷键 §eO§f 控制台 / §eF8§f 总控");
                        return 1;
                    })));
        });
    }

    private static void onTick(MinecraftClient client) {
        // 处理命令里推迟打开界面（等待聊天框关闭完成，避免被顶掉）
        if (openPanelTab != null && client.currentScreen == null) {
            SkyScriptScreen.Tab t = openPanelTab;
            openPanelTab = null;
            client.setScreen(new SkyScriptScreen(t));
        }
        if (openHudEdit && client.currentScreen == null) {
            openHudEdit = false;
            client.setScreen(new HudEditScreen());
        }
        if (wasActivated(client, masterKey, SkyScriptConfig.get().masterKeyName)) {
            MasterController.onMasterPressed();
        }
        if (wasActivated(client, settingsKey, SkyScriptConfig.get().settingsKeyName) && client.currentScreen == null) {
            client.setScreen(new SkyScriptScreen());
        }
        ScriptEngine.INSTANCE.tick(client);
    }

    /**
     * 双通道检测：KeyBinding.wasPressed() + GLFW 轮询按下沿。
     * 1.21.11 输入重构后 KeyBinding 的按下状态时序不可靠，轮询通道保证按键必响应。
     * 按下沿带 80ms 防抖，避免异常抖动重复触发。
     */
    private static final long KEY_DEBOUNCE_MS = 80;
    private static final Map<String, Long> lastKeyFire = new HashMap<>();

    private static boolean wasActivated(MinecraftClient client, KeyBinding binding, String configKey) {
        boolean pressed = false;
        Integer code = KeyNames.glfwOf(configKey);
        if (code != null && client.getWindow() != null) {
            pressed = InputUtil.isKeyPressed(client.getWindow(), code);
        }
        boolean prev = prevKeyStates.getOrDefault(configKey, false);
        prevKeyStates.put(configKey, pressed);
        boolean edge = pressed && !prev;
        if (edge || (binding != null && binding.wasPressed())) {
            long now = System.currentTimeMillis();
            Long last = lastKeyFire.get(configKey);
            if (last != null && now - last < KEY_DEBOUNCE_MS) return false;
            lastKeyFire.put(configKey, now);
            return true;
        }
        return false;
    }
}

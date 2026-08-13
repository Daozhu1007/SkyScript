package dev.skyscript;

import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.engine.ScriptEngine;
import dev.skyscript.hud.ScriptHud;
import dev.skyscript.screen.ScriptListScreen;
import net.fabricmc.api.ClientModInitializer;
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

public class SkyScriptClient implements ClientModInitializer {

    private static final KeyBinding.Category CATEGORY =
            new KeyBinding.Category(Identifier.of("sky_script", "category"));

    public static KeyBinding masterKey;
    public static KeyBinding editorKey;

    @Override
    public void onInitializeClient() {
        masterKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sky_script.master", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY));
        editorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sky_script.open_editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));

        SkyScriptConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(SkyScriptClient::onTick);
        HudRenderCallback.EVENT.register(ScriptHud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ScriptEngine.INSTANCE.stop());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ScriptEngine.INSTANCE.stop());
    }

    private static void onTick(MinecraftClient client) {
        if (masterKey.wasPressed()) {
            MasterController.onMasterPressed();
        }
        if (editorKey.wasPressed() && client.currentScreen == null) {
            client.setScreen(new ScriptListScreen());
        }
        ScriptEngine.INSTANCE.tick(client);
    }
}

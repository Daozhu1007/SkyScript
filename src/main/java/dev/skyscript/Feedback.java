package dev.skyscript;

import dev.skyscript.config.SkyScriptConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * 聊天反馈消息（本地可见，不发给服务器），受设置 master.feedback 控制。
 */
public final class Feedback {

    private Feedback() {
    }

    public static void notify(String message) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null) return;
        if (!SkyScriptConfig.get().master.feedback) return;
        c.player.sendMessage(Text.literal(message), false);
    }
}

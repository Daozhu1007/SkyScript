package dev.skyscript;

import dev.skyscript.config.SkyScriptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 聊天反馈消息（本地可见，不发给服务器），受设置 master.feedback 控制。
 * 相同消息 500ms 内去重，防止异常情况下刷屏。
 */
public final class Feedback {

    private static String lastMessage = "";
    private static long lastTime = 0;

    private Feedback() {
    }

    public static void notify(String message) {
        Minecraft c = Minecraft.getInstance();
        if (c == null || c.player == null) return;
        if (!SkyScriptConfig.get().master.feedback) return;
        long now = System.currentTimeMillis();
        if (message.equals(lastMessage) && now - lastTime < 500) return;
        lastMessage = message;
        lastTime = now;
        c.player.sendSystemMessage(Component.literal(message));
    }
}

package dev.skyscript.mixin;

import dev.skyscript.input.KeyEvents;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获真实的键盘按下/松开事件（KeyboardHandler.keyPress），供触发键使用。
 * 事件驱动保证快速点击也不会漏检。
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyEventCatcher {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void skyScript$keyPress(long window, int action, KeyEvent input, CallbackInfo ci) {
        // 脚本自己注入的事件（KeySimulator）不应被当作真实按键记录，否则会触发"幻影点击"
        if (KeyEvents.isInjecting()) return;
        if (action == GLFW.GLFW_PRESS) {
            KeyEvents.onKeyDown(input.key());
        } else if (action == GLFW.GLFW_RELEASE) {
            KeyEvents.onKeyUp(input.key());
        }
    }
}

package dev.skyscript.mixin;

import dev.skyscript.input.KeyEvents;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获真实的按键抬起事件（Keyboard.onKey），供触发键使用。
 * 事件驱动保证快速点击也不会漏检。
 */
@Mixin(Keyboard.class)
public abstract class KeyEventCatcher {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void skyScript$onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (action == GLFW.GLFW_RELEASE) {
            KeyEvents.onKeyUp(input.key());
        }
    }
}

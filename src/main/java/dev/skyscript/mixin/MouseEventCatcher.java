package dev.skyscript.mixin;

import dev.skyscript.input.KeyEvents;
import dev.skyscript.input.MouseButtons;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 记录真实鼠标左键按下/松开状态（供 HUD 拖动编辑使用）。
 * 脚本注入的事件跳过，避免误判。
 */
@Mixin(MouseHandler.class)
public abstract class MouseEventCatcher {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void skyScript$onButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        if (KeyEvents.isInjecting()) return;
        if (input.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            MouseButtons.setLeft(action == GLFW.GLFW_PRESS);
        }
    }
}

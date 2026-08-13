package dev.skyscript.mixin;

import dev.skyscript.input.MovementController;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11 的 Input 重构：KeyboardInput#tick() 轮询真实按键 → 写入 playerInput 并算出 movementVector。
 * 在 tick 返回后覆写这两个字段为脚本期望状态（引擎空闲时 MovementController 为空 → 零影响）。
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void skyscript$apply(CallbackInfo ci) {
        if (!MovementController.active()) return;
        float side = MovementController.getSideways();
        float forward = MovementController.getForward();
        boolean jump = MovementController.isJumping();
        boolean sneak = MovementController.isSneaking();
        InputAccessor accessor = (InputAccessor) (Object) this;
        accessor.skyScript$setPlayerInput(new PlayerInput(forward > 0, forward < 0, side < 0, side > 0, jump, sneak, false));
        accessor.skyScript$setMovementVector(new Vec2f(side, forward));
    }
}

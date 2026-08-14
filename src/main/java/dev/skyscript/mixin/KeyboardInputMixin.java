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
 *
 * <p>方向约定（与 vanilla 一致）：
 * movementVector = new Vec2f(左右, 前后)，其中左右轴 x=+1 为左（vanilla 的
 * getMovementMultiplier(left, right) 在 left 按下时返回 +1）。
 * MovementController.getSideways() 返回 A=-1/D=+1，因此 x 写入 -side。
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
        accessor.skyScript$setMovementVector(new Vec2f(-side, forward));
    }
}

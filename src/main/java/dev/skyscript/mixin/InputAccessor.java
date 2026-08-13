package dev.skyscript.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 的 Input 字段访问器（playerInput 在 Input 类上声明，经此写入）。
 */
@Mixin(Input.class)
public interface InputAccessor {

    @Accessor("playerInput")
    void skyScript$setPlayerInput(PlayerInput value);

    @Accessor("movementVector")
    void skyScript$setMovementVector(Vec2f value);
}

package dev.skyscript.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 1.21.11 起 Mouse#onMouseButton 变成 private，用 @Invoker 暴露给 KeySimulator 做鼠标事件注入。
 */
@Mixin(Mouse.class)
public interface MouseAccessor {

    @Invoker("onMouseButton")
    void skyScript$onMouseButton(long window, MouseInput input, int action);
}

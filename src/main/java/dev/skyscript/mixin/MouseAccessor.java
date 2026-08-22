package dev.skyscript.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 1.21.11 起 Mouse#onMouseButton 变成 private，用 @Invoker 暴露给 KeySimulator 做鼠标事件注入。
 */
@Mixin(MouseHandler.class)
public interface MouseAccessor {

    @Invoker("onMouseButton")
    void skyScript$onButton(long window, MouseButtonInfo input, int action);
}

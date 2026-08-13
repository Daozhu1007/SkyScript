package dev.skyscript.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 1.21.11 起 Keyboard#onKey 变成 private，用 @Invoker 暴露给 KeySimulator 做游戏内事件注入。
 */
@Mixin(Keyboard.class)
public interface KeyboardAccessor {

    @Invoker("onKey")
    void skyScript$onKey(long window, int action, KeyInput input);
}

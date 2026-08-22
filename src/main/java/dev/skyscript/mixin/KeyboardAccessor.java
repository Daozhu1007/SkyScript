package dev.skyscript.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 26.2 起 KeyboardHandler#keyPress 是 private，用 @Invoker 暴露给 KeySimulator 做游戏内事件注入。
 */
@Mixin(KeyboardHandler.class)
public interface KeyboardAccessor {

    @Invoker("keyPress")
    void skyScript$keyPress(long window, int action, KeyEvent input);
}

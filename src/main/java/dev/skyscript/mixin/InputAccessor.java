package dev.skyscript.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.2 的 ClientInput 字段访问器（keyPresses/moveVector 由 ClientInput 声明）。
 */
@Mixin(ClientInput.class)
public interface InputAccessor {

    @Accessor("keyPresses")
    void skyScript$setKeyPresses(Input value);

    @Accessor("moveVector")
    void skyScript$setMoveVector(Vec2 value);
}

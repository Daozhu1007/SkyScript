package dev.skyscript;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * 攻击/摧毁模式（切换 / 长按）读写。
 *
 * <p>MC 26.2 中 {@code Options#toggleAttack()} 直接返回 {@code OptionInstance<Boolean>}
 * （true=切换模式），其取值 API 为类型安全的 {@link OptionInstance#get()} / {@link OptionInstance#set(Object)}，
 * 无需反射。
 */
public final class AttackModeHelper {

    private static OptionInstance<Boolean> option;
    /** 是否已成功解析选项；未成功前每次调用都会重试（客户端可能尚未就绪）。 */
    private static boolean resolved;

    private AttackModeHelper() {
    }

    private static synchronized boolean init() {
        if (resolved) return option != null;

        Minecraft c = Minecraft.getInstance();
        if (c == null || c.options == null) {
            return false;
        }

        option = c.options.toggleAttack();
        if (option == null) {
            return false;
        }

        resolved = true;
        return true;
    }

    public static boolean available() {
        return init();
    }

    public static boolean isToggle() {
        if (!init()) return false;
        return Boolean.TRUE.equals(option.get());
    }

    /** 切换到切换模式(toggle=true)或长按模式(toggle=false)；返回是否成功 */
    public static boolean setToggle(boolean toggle) {
        if (!init()) return false;

        if (Boolean.TRUE.equals(option.get()) == toggle) {
            return true;
        }

        option.set(toggle);
        return true;
    }
}

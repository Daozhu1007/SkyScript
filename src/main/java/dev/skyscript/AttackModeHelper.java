package dev.skyscript;

import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 攻击/摧毁模式（HOLD / TOGGLE）读写。
 *
 * <p>这是 1.21.2+ 加入的 GameOptions 里的一个 SimpleOption（枚举 AttackMode: HOLD/TOGGLE）。
 * 字段名/类名随版本可能变化，这里用反射 + 启发式查找（名字含 attackMode，或值枚举含
 * HOLD/TOGGLE），保证跨版本健壮 —— 版本隔离的关键点之一。
 */
public final class AttackModeHelper {

    private static Field optionField;
    private static Object option;
    private static Method getValue;
    private static Method setValue;
    private static boolean tried;

    private AttackModeHelper() {
    }

    private static synchronized boolean init() {
        if (tried) return optionField != null;
        tried = true;
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.options == null) return false;
            Class<?> optCls = c.options.getClass();
            for (Class<?> cls = optCls; cls != null; cls = cls.getSuperclass()) {
                for (Field f : cls.getDeclaredFields()) {
                    if ("attackMode".equals(f.getName())) {
                        optionField = f;
                        break;
                    }
                    if (!"net.minecraft.client.option.SimpleOption".equals(f.getType().getName())) continue;
                    f.setAccessible(true);
                    Object opt = f.get(c.options);
                    if (opt == null) continue;
                    try {
                        Object val = opt.getClass().getMethod("getValue").invoke(opt);
                        if (val != null && val.getClass().isEnum() && hasHoldToggle(val.getClass())) {
                            optionField = f;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (optionField != null) break;
            }
            if (optionField == null) return false;
            optionField.setAccessible(true);
            option = optionField.get(c.options);
            getValue = option.getClass().getMethod("getValue");
            setValue = option.getClass().getMethod("setValue", Object.class);
            return true;
        } catch (Exception e) {
            optionField = null;
            return false;
        }
    }

    private static boolean hasHoldToggle(Class<?> enumCls) {
        boolean hold = false, toggle = false;
        for (Object e : enumCls.getEnumConstants()) {
            String n = ((Enum<?>) e).name();
            if (n.contains("HOLD")) hold = true;
            if (n.contains("TOGGLE")) toggle = true;
        }
        return hold && toggle;
    }

    public static boolean available() {
        return init();
    }

    public static boolean isToggle() {
        if (!init()) return false;
        try {
            Object v = getValue.invoke(option);
            return v != null && v.toString().toUpperCase().contains("TOGGLE");
        } catch (Exception e) {
            return false;
        }
    }

    /** 切换到切换模式(toggle=true)或长按模式(toggle=false)；返回是否成功 */
    public static boolean setToggle(boolean toggle) {
        if (!init()) return false;
        try {
            Object v = getValue.invoke(option);
            if (v == null) return false;
            @SuppressWarnings({"unchecked", "rawtypes"})
            Enum<?> target = Enum.valueOf((Class<? extends Enum>) v.getClass(), toggle ? "TOGGLE" : "HOLD");
            if (v == target) return true;
            setValue.invoke(option, target);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

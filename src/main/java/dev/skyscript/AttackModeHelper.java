package dev.skyscript;

import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 攻击/摧毁模式（切换 / 长按）读写。
 *
 * <p>1.21.2+ 的 GameOptions 里该选项经历了几种形态：
 * <ul>
 *   <li>1.21.11 及之后：{@code attackToggled}（SimpleOption&lt;Boolean&gt;，true=切换模式）</li>
 *   <li>早期版本：枚举（HOLD/TOGGLE）形式的 SimpleOption</li>
 * </ul>
 * 用反射 + 启发式查找（优先名字 attackToggled，回退值枚举含 HOLD/TOGGLE），跨版本健壮。
 */
public final class AttackModeHelper {

    private static Object option;
    private static Method getValue;
    private static Method setValue;
    private static boolean tried;

    private AttackModeHelper() {
    }

    private static synchronized boolean init() {
        if (tried) return option != null;
        tried = true;
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.options == null) return false;
            // 1.21.11+：优先用公开 getter getAttackToggled()（比反射私有字段稳，绕开模块封装）
            try {
                Method getter = c.options.getClass().getMethod("getAttackToggled");
                option = getter.invoke(c.options);
            } catch (NoSuchMethodException ignored) {
                option = findFieldOption(c);
            }
            if (option == null) return false;
            getValue = option.getClass().getMethod("getValue");
            setValue = option.getClass().getMethod("setValue", Object.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 回退：反射找 attackToggled 字段 / HOLD-TOGGLE 枚举字段 */
    private static Object findFieldOption(MinecraftClient c) throws Exception {
        Class<?> optCls = c.options.getClass();
        for (Class<?> cls = optCls; cls != null; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if ("attackToggled".equals(f.getName())) {
                    f.setAccessible(true);
                    return f.get(c.options);
                }
                if (!"net.minecraft.client.option.SimpleOption".equals(f.getType().getName())) continue;
                f.setAccessible(true);
                Object opt = f.get(c.options);
                if (opt == null) continue;
                try {
                    Object val = opt.getClass().getMethod("getValue").invoke(opt);
                    if (val != null && val.getClass().isEnum() && hasHoldToggle(val.getClass())) {
                        return opt;
                    }
                } catch (Exception ignored) {
                }
            }
            if (cls.getSuperclass() == null) break;
        }
        return null;
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
            if (v instanceof Boolean b) return b;
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
            if (v instanceof Boolean b) {
                if (b == toggle) return true;
                setValue.invoke(option, toggle);
                return true;
            }
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

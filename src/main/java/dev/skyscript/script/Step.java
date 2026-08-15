package dev.skyscript.script;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本步骤模型（对应 JSON 里 steps[] 的一项）。
 * 字段故意用扁平结构 + 公共字段，方便 Gson 直接读写与游戏内编辑器操作。
 */
public class Step {

    /** 步骤类型：hold / wait / press / command / loop */
    public String type = "hold";

    /** hold/press 使用的按键名列表，如 ["A"]、["A","W"]。键名见 KeyNames。 */
    public List<String> keys = new ArrayList<>();

    /** wait 毫秒数 / hold 的 time 触发毫秒数 / press(hold) 的毫秒数。-1 表示未设置。 */
    public int ms = -1;

    /** hold 的结束条件类型：time / position / manual */
    public String untilType = "time";

    /** position 条件（多条 = 同时满足） */
    public List<PosCond> cond = new ArrayList<>();

    /** command 指令内容，如 "/home"（带不带斜杠均可） */
    public String value = "";

    /** press 的模式：tap / hold */
    public String mode = "tap";

    /** loop 的重复次数（>=1） */
    public int times = 1;

    /** loop 的循环体 */
    public List<Step> body = new ArrayList<>();

    public Step() {
    }

    public Step copy() {
        Step s = new Step();
        s.type = type;
        s.keys = new ArrayList<>(keys);
        s.ms = ms;
        s.untilType = untilType;
        s.cond = new ArrayList<>();
        for (PosCond c : cond) s.cond.add(c.copy());
        s.value = value;
        s.mode = mode;
        s.times = times;
        for (Step b : body) s.body.add(b.copy());
        return s;
    }

    /** 生成一行人话摘要（编辑器列表用）：按类型 + 结束条件正确拼句 */
    public String summary() {
        return switch (type) {
            case "wait" -> "等待 " + sec(Math.max(0, ms));
            case "hold" -> "按 " + keysDesc() + " " + holdSuffix();
            case "press" -> "hold".equals(mode)
                    ? "按住 " + keysDesc() + " " + holdSuffix()
                    : "点按 " + keysDesc();
            case "command" -> "发送指令 " + (value == null || value.isEmpty() ? "/…" : value);
            case "loop" -> "循环 " + Math.max(1, times) + " 次（" + body.size() + " 步）";
            default -> type;
        };
    }

    private String keysDesc() {
        if (keys == null || keys.isEmpty()) return "（未设按键）";
        return String.join(" + ", keys);
    }

    /** 长按类动作的结束条件后缀 */
    private String holdSuffix() {
        return switch (untilType == null ? "time" : untilType) {
            case "position" -> "，直到坐标 " + condsText();
            case "manual" -> "，手动结束";
            default -> "，持续 " + sec(Math.max(0, ms));
        };
    }

    /** 有效坐标条件，逗号连接：x≤100, y≥64；忽略的轴不显示 */
    private String condsText() {
        StringBuilder sb = new StringBuilder();
        if (cond != null) {
            for (PosCond pc : cond) {
                if (pc == null || pc.op == null) continue;
                if ("忽略".equals(pc.op) || "ignore".equals(pc.op)) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(pc.axis).append(condOp(pc.op)).append(trimZero(pc.value));
            }
        }
        return sb.length() == 0 ? "（未设）" : sb.toString();
    }

    private static String condOp(String op) {
        return switch (op) {
            case "<=" -> "≤";
            case ">=" -> "≥";
            case "==" -> "=";
            case "<" -> "<";
            case ">" -> ">";
            default -> op;
        };
    }

    private static String trimZero(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    /** 毫秒 → 秒的人话（去掉多余的 .0） */
    private static String sec(int ms) {
        if (ms <= 0) return "0 秒";
        double s = ms / 1000.0;
        String t = trimZero(s);
        return t + " 秒";
    }
}

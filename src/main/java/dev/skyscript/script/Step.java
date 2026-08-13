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

    /** 生成一行摘要（编辑器列表用） */
    public String summary() {
        return switch (type) {
            case "wait" -> "wait " + Math.max(0, ms) + "ms";
            case "hold" -> "hold " + keys + " → " + untilDesc();
            case "press" -> "press " + keys + " (" + mode + ")" + ("hold".equals(mode) ? " → " + untilDesc() : "");
            case "command" -> "cmd " + value;
            case "loop" -> "loop ×" + times + " (" + body.size() + " 子步骤)";
            default -> type;
        };
    }

    private String untilDesc() {
        return switch (untilType == null ? "time" : untilType) {
            case "time" -> Math.max(0, ms) + "ms";
            case "position" -> cond.toString();
            case "manual" -> "手动";
            default -> "time";
        };
    }
}

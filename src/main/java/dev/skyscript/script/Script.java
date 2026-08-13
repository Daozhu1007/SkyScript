package dev.skyscript.script;

import java.util.ArrayList;
import java.util.List;

/**
 * 一份脚本方案。loop=0 表示无限循环（默认），N 表示整份跑 N 轮后自动停。
 */
public class Script {

    public String name = "新方案";
    public int loop = 0;
    public List<Step> steps = new ArrayList<>();

    public Script() {
    }

    public Script(String name) {
        this.name = name;
    }

    /** 深拷贝（引擎启动时使用，避免污染配置文件里的原始数据） */
    public Script copy() {
        Script s = new Script();
        s.name = name;
        s.loop = loop;
        for (Step st : steps) s.steps.add(st.copy());
        return s;
    }

    /** 新建方案的默认模板：AHK 风格的 A/D 交替，各 120 秒，间隔 0.5 秒，无限循环 */
    public static Script createDefault(String name) {
        Script s = new Script(name);
        s.loop = 0;
        Step a = new Step();
        a.type = "hold";
        a.keys.add("A");
        a.untilType = "time";
        a.ms = 120000;
        Step w = new Step();
        w.type = "wait";
        w.ms = 500;
        Step d = new Step();
        d.type = "hold";
        d.keys.add("D");
        d.untilType = "time";
        d.ms = 120000;
        s.steps.add(a);
        s.steps.add(w);
        s.steps.add(d);
        s.steps.add(w.copy());
        return s;
    }
}

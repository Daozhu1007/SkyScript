package dev.skyscript.script;

/**
 * 位置触发条件：轴 + 比较符 + 值，如 x <= 100.5。
 */
public class PosCond {

    /** x / y / z */
    public String axis = "x";

    /** <= / >= / < / > / == */
    public String op = "<=";

    public double value = 0;

    public PosCond() {
    }

    public PosCond(String axis, String op, double value) {
        this.axis = axis;
        this.op = op;
        this.value = value;
    }

    public PosCond copy() {
        return new PosCond(axis, op, value);
    }

    @Override
    public String toString() {
        return axis + " " + op + " " + value;
    }

    /** 判定某个坐标分量是否满足条件 */
    public boolean test(double v) {
        return switch (op) {
            case "<=" -> v <= value;
            case ">=" -> v >= value;
            case "<" -> v < value;
            case ">" -> v > value;
            case "==" -> Math.abs(v - value) < 1e-3;
            default -> v <= value;
        };
    }
}

package dev.skyscript.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.skyscript.Feedback;
import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.input.KeyEvents;
import dev.skyscript.input.KeyNames;
import dev.skyscript.input.KeySimulator;
import dev.skyscript.input.MovementController;
import dev.skyscript.input.OsKeySimulator;
import dev.skyscript.script.Script;
import dev.skyscript.script.Step;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 脚本运行时状态机。
 *
 * <p>执行模型：一个执行栈（Frame）。栈底是脚本级帧（steps + 方案级 loop 计数），
 * loop 步骤会压入循环体帧。步骤按类型执行：
 * <ul>
 *   <li>hold：按住一组键直到 time / position / manual 条件满足；</li>
 *   <li>wait：等 ms 毫秒；</li>
 *   <li>press：tap（立即点按）/ hold（按住直到条件满足）；</li>
 *   <li>command：直接发送指令（不进聊天框）；</li>
 *   <li>loop：循环体重复 times 次。</li>
 * </ul>
 *
 * <p>方向（A/D）语义：横向 hold 步骤按"位置交替"模式映射 —— 脚本里第 i 个横向
 * hold 步骤的有效键由 lateralBias（本轮第一个横向键）决定，A,D,A,D… 交替。
 * 空闲时按触发键（A/D）启动 → bias = 该键；运行中按另一个方向键 → 切换 bias
 * （当前列立即变成新键，后续列继续交替）。与原 AHK 脚本行为一致。
 *
 * <p>停止条件：F8/手动停、断线、死亡；打开任何界面 → 冻结计时（不移动、不消耗时间）。
 *
 * <p>坐标到达判定（确定性容差模型）：每个有条件的轴要求 |当前-目标| ≤ 0.5（半格），
 * X/Y/Z 策略统一，不做浮点相等比较，也不依赖移动方向——玩家在目标方块列内即判到达。
 * 卡死检测：位置步骤的误差长时间不减小 → 节流告警（不自动停）。
 *
 * <p>状态机：单一 Phase 变量维护生命周期（IDLE/RUNNING/FROZEN/ERROR），步骤推进只走
 * advanceIfDue → advance → enterStep 单一路径；所有异常都会 stop() 并清理全部按键状态。
 * 开启 debugMode 后聊天栏打印执行全过程（步骤/坐标/误差/按键/切换/停止原因）。
 */
public final class ScriptEngine {

    public static final ScriptEngine INSTANCE = new ScriptEngine();

    private static final Gson GSON = new GsonBuilder().create();

    /** 坐标到达容差（半格）：|cur-target| ≤ 0.5 ⇔ 玩家中心在目标方块列内（floor(cur+0.5)==target） */
    public static final double REACH_TOLERANCE = 0.5;

    /** 卡死检测：误差超过该时长无任何改善 → 告警 */
    private static final long STUCK_MS = 5000;
    private static final long STUCK_WARN_INTERVAL_MS = 10000;
    private static final long POS_LOG_INTERVAL_MS = 1000;

    /** 引擎生命周期阶段（步骤级推进由 stack+curStep 维护，见类注释） */
    public enum Phase { IDLE, RUNNING, FROZEN, ERROR }

    /** 执行帧：一段步骤列表 + 当前下标 + 剩余循环次数 */
    private static final class Frame {
        final List<Step> steps;
        final int total;      // 循环总次数（脚本帧 = script.loop；loop 帧 = times）
        int index;
        int remaining;

        Frame(List<Step> steps, int total) {
            this.steps = steps;
            this.total = total;
            this.remaining = total;
        }

        boolean isScriptFrame() {
            return false;
        }
    }

    private final ArrayDeque<Frame> stack = new ArrayDeque<>();
    private Script script;
    private Step curStep;
    private Phase phase = Phase.IDLE;
    private boolean armed;   // F8 总开关：只有 arm 后触发键才响应（恢复 AHK「#HotIf FeatureOn()」语义）
    private long frozenSince;
    private long phaseStartMs;
    private long holdUntilMs;
    private String lateralBias = "A";
    private String lateralKey;                 // 当前步骤的横向键（无则 null）
    private final Set<String> robotHeld = new HashSet<>();
    private final Map<String, Boolean> prevTrigger = new HashMap<>();
    private long implicitTapUntil;
    private final Set<String> implicitTapKeys = new HashSet<>();
    // 位置步骤看门狗：卡死检测 + 调试显示（误差 = 当前值-目标值）
    private double bestDist = Double.MAX_VALUE; // 进入当前步骤以来的最小最大轴误差
    private long lastProgressMs;
    private long lastStuckWarnMs;
    private long lastPosLogMs;
    private String lastPosText = "";            // 最近一次采样的 "pos=(..) 误差(..)" 文本
    private long roundCount;                    // 无限循环已完成的轮数（调试用）

    private ScriptEngine() {
    }

    // ---------- 状态查询（HUD 用） ----------

    public boolean isRunning() {
        return phase == Phase.RUNNING || phase == Phase.FROZEN;
    }

    public Phase getPhase() {
        return phase;
    }

    /** F8 总开关状态：true=已开启（触发键可用），false=已关闭（A/D 完全恢复正常移动） */
    public boolean isArmed() {
        return armed;
    }

    /** 设置总开关；关闭时若脚本在运行则一并停止（保证 running ⇒ armed）。 */
    public void setArmed(boolean armed) {
        this.armed = armed;
        if (!armed) {
            stop("F8 总开关关闭");
        }
    }

    public boolean isFrozen() {
        return phase == Phase.FROZEN;
    }

    public Script getScript() {
        return script;
    }

    public Step getCurStep() {
        return curStep;
    }

    public String getStateText() {
        return switch (phase) {
            case RUNNING -> "运行";
            case FROZEN -> "暂停";
            case ERROR -> "错误";
            default -> armed ? "待命" : "空闲";
        };
    }

    /** 当前步骤进度（内层帧，[index+1, total]）；未运行返回 [0,0] */
    public int[] getStepProgress() {
        Frame f = stack.peek();
        if (f == null) return new int[]{0, 0};
        return new int[]{f.index + 1, f.steps.size()};
    }

    /** 调试行（HUD 第二行）：步骤/按键/当前坐标/目标误差；未开 debugMode 或未运行返回 null */
    public String getDebugLine() {
        if (!SkyScriptConfig.get().debugMode || !isRunning() || curStep == null) return null;
        int[] p = getStepProgress();
        String keys = curStep.keys == null || curStep.keys.isEmpty() ? "-" : String.join("+", curStep.keys);
        return "步骤" + p[0] + "/" + p[1] + " 键:" + keys + " " + lastPosText;
    }

    public String getStepSummary() {
        return curStep == null ? "—" : curStep.summary();
    }

    public int getTimeLeftSeconds() {
        if (!isRunning() || curStep == null || !"time".equals(curStep.untilType)) return -1;
        long left = (holdUntilMs - nowMs()) / 1000;
        return (int) Math.max(0, left);
    }

    /** 最内层 loop 帧进度（col/total）；无 loop 帧返回 -1/-1 */
    public int[] getLoopProgress() {
        if (stack.size() <= 1) return new int[]{-1, -1};
        Frame f = stack.peek();
        return new int[]{f.total - f.remaining, f.total};
    }

    // ---------- 生命周期 ----------

    public void start(Script original) {
        stop();
        if (original == null || original.steps == null || original.steps.isEmpty()) {
            // 空方案不启动，避免 enterStep 越界崩溃
            Feedback.notify("§6[SkyScript] §f这个方案还没有动作，先编辑加动作再启动");
            return;
        }
        script = GSON.fromJson(GSON.toJson(original), Script.class);
        stack.clear();
        stack.push(new Frame(script.steps, script.loop));
        phase = Phase.RUNNING;
        roundCount = 0;
        debug("Started: " + script.name + "（共 " + script.steps.size() + " 步，循环="
                + (script.loop == 0 ? "无限" : script.loop) + "）");
        enterStep(stack.peek(), 0);
    }

    public void stop() {
        stop(null);
    }

    /** 停止并清理全部输入状态；reason 非空时在调试日志里记录停止原因 */
    public void stop(String reason) {
        boolean wasRunning = isRunning();
        phase = Phase.IDLE;
        script = null;
        curStep = null;
        stack.clear();
        releaseRobotKeys();
        MovementController.clear();
        implicitTapKeys.clear();
        lateralBias = "A";
        lateralKey = null;
        bestDist = Double.MAX_VALUE;
        lastPosText = "";
        if (wasRunning && reason != null) {
            debug("Stopped: " + reason);
        }
    }

    // ---------- 每 tick 驱动 ----------

    public void tick(MinecraftClient client) {
        try {
            pollTriggers(client);
            if (!isRunning()) {
                // 防御性清理：任何非运行态都不允许残留按住状态
                if (MovementController.active()) MovementController.clear();
                return;
            }
            if (client.player == null || client.getNetworkHandler() == null) {
                stop("玩家或网络连接不存在");
                return;
            }
            if (client.player.isDead()) {
                stop("玩家死亡");
                return;
            }

            boolean screenOpen = client.currentScreen != null;
            if (screenOpen) {
                if (phase != Phase.FROZEN) {
                    phase = Phase.FROZEN;
                    frozenSince = nowMs();
                    MovementController.clear();
                    releaseRobotKeys();   // 打开界面时释放按住的键，避免卡键
                    debug("界面打开 → 暂停（已释放全部按键，计时冻结）");
                }
                return;
            }
            if (phase == Phase.FROZEN) {
                long d = nowMs() - frozenSince;
                phaseStartMs += d;
                holdUntilMs += d;
                lastProgressMs += d;
                phase = Phase.RUNNING;
                // 界面关闭恢复：重新按住当前 hold 步骤的非移动键（冻结时被释放）
                if (curStep != null && isHoldLike(curStep) && curStep.keys != null) {
                    pressRobotKeys(curStep.keys);
                }
                debug("界面关闭 → 恢复运行");
            }

            long now = nowMs();
            advanceIfDue(now);

            // 应用移动输入（唯一写入口：当前步骤按键 → MovementController）
            if (phase == Phase.RUNNING && curStep != null) {
                if (now < implicitTapUntil) {
                    MovementController.setDesired(implicitTapKeys);
                } else if (isHoldLike(curStep)) {
                    MovementController.setDesired(curStep.keys);
                } else {
                    MovementController.clear();
                }
            } else {
                MovementController.clear();
            }

            updatePositionWatchdog(client, now);
        } catch (Throwable t) {
            // 任何异常都不允许裸奔：先置 ERROR 再 stop()，确保按键全部释放
            phase = Phase.ERROR;
            stop("内部错误 " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void advanceIfDue(long now) {
        if (curStep == null) return;
        switch (curStep.type) {
            case "wait" -> {
                if (now >= holdUntilMs) advance();
            }
            case "hold", "press" -> {
                if ("press".equals(curStep.type) && "tap".equals(curStep.mode)) return;
                String ut = curStep.untilType == null ? "time" : curStep.untilType;
                if ("time".equals(ut)) {
                    if (now >= holdUntilMs) advance();
                } else if ("position".equals(ut)) {
                    if (posCondMet()) advance();
                }
            }
            default -> {
            }
        }
    }

    private void advance() {
        Frame f = stack.peek();
        if (f == null) {
            stop("执行帧为空");
            return;
        }
        // 步骤完成：先释放输入（enterStep 会再 release 一次兜底），再推进下标
        debug("Step " + (f.index + 1) + "/" + f.steps.size() + " 完成 · 释放按键 "
                + (curStep == null || curStep.keys == null ? "[]" : curStep.keys));
        releaseRobotKeys();
        MovementController.clear();
        f.index++;
        if (f.index < f.steps.size()) {
            debug("Switching to Step " + (f.index + 1) + "/" + f.steps.size());
            enterStep(f, f.index);
            return;
        }
        // 本帧跑完
        stack.pop();
        debug("当前步骤列表执行完毕（剩余循环次数=" + (f.remaining == 0 ? "无限" : f.remaining) + "）");
        if (stack.isEmpty()) {
            // 脚本级帧完成：无限循环(loop=0)则重来，否则整份结束
            if (f.remaining == 0) {
                roundCount++;
                debug("第 " + roundCount + " 轮完成（无限循环）→ 重新开始");
                stack.push(new Frame(script.steps, script.loop));
                enterStep(stack.peek(), 0);
            } else if (f.remaining > 1) {
                debug("第 1 轮完成，剩余 " + (f.remaining - 1) + " 轮 → 重新开始");
                stack.push(new Frame(script.steps, f.remaining - 1));
                enterStep(stack.peek(), 0);
            } else {
                debug("方案全部执行完毕 → Finished");
                stop();
            }
            return;
        }
        // 内层帧完成 → 父帧继续
        advance();
    }

    private void enterStep(Frame f, int index) {
        if (f == null || f.steps == null || f.steps.isEmpty() || index < 0 || index >= f.steps.size()) {
            stop("步骤下标越界");
            return;
        }
        f.index = index;
        curStep = f.steps.get(index);
        phaseStartMs = nowMs();
        releaseRobotKeys();   // 步骤切换：先释放上一轮的非移动键，避免泄漏到下个步骤
        int[] prog = {index + 1, f.steps.size()};
        switch (curStep.type) {
            case "wait" -> {
                holdUntilMs = nowMs() + Math.max(0, curStep.ms < 0 ? 0 : curStep.ms);
                debug("Step " + prog[0] + "/" + prog[1] + " started · 等待 " + Math.max(0, curStep.ms) + "ms");
            }
            case "hold" -> setupHold();
            case "press" -> {
                if ("hold".equals(curStep.mode)) {
                    setupHold();
                } else {
                    debug("Step " + prog[0] + "/" + prog[1] + " started · 点按 " + curStep.keys);
                    performTap(curStep);
                    advance();
                }
            }
            case "command" -> {
                MinecraftClient c = MinecraftClient.getInstance();
                String cmd = curStep.value == null ? "" : curStep.value.trim();
                if (c.getNetworkHandler() != null && !cmd.isEmpty()) {
                    String send = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                    debug("Step " + prog[0] + "/" + prog[1] + " · 发送指令: /" + send);
                    c.getNetworkHandler().sendChatCommand(send);
                } else {
                    debug("Step " + prog[0] + "/" + prog[1] + " · 指令跳过（空指令或无连接）");
                }
                advance();
            }
            case "loop" -> {
                int t = Math.max(1, curStep.times);
                if (curStep.body.isEmpty()) {
                    advance(); // 空循环体直接跳过
                } else {
                    debug("进入循环（" + t + " 遍，" + curStep.body.size() + " 步）");
                    stack.push(new Frame(curStep.body, t));
                    enterStep(stack.peek(), 0);
                }
            }
            default -> advance();
        }
    }

    private void setupHold() {
        List<String> keys = curStep.keys == null ? new ArrayList<>() : curStep.keys;
        // 横向键识别
        lateralKey = null;
        if (keys.size() == 1) {
            String k = keys.get(0);
            if ("A".equals(k) || "D".equals(k)) lateralKey = k;
        }
        // 非移动键 → Robot 按住（enterStep 已先 releaseRobotKeys 清理上一轮残留）；
        // 移动键不走 OS 层，由每 tick 的 MovementController.setDesired 持续注入
        pressRobotKeys(keys);
        int[] prog = getStepProgress();
        String ut = curStep.untilType == null ? "time" : curStep.untilType;
        if ("time".equals(ut)) {
            holdUntilMs = nowMs() + Math.max(0, curStep.ms < 0 ? 120000 : curStep.ms);
            debug("Step " + prog[0] + "/" + prog[1] + " started · Action: 按住 " + keys
                    + " · 持续 " + Math.max(0, curStep.ms < 0 ? 120000 : curStep.ms) + "ms");
        } else if ("position".equals(ut)) {
            holdUntilMs = Long.MAX_VALUE;
            // 看门狗复位：新步骤重新累计误差/进度
            bestDist = Double.MAX_VALUE;
            lastProgressMs = nowMs();
            lastStuckWarnMs = 0;
            lastPosLogMs = 0;
            lastPosText = "";
            debug("Step " + prog[0] + "/" + prog[1] + " started · Action: 按住 " + keys
                    + " · Target: " + targetText() + " · 容差±" + REACH_TOLERANCE);
            if (curStep.cond == null || activeCondCount() == 0) {
                debug("⚠ 该步骤没有有效坐标条件，永远不会完成（去编辑里设置 X/Y/Z）");
            }
        } else {
            // manual：按住直到手动停止
            holdUntilMs = Long.MAX_VALUE;
            debug("Step " + prog[0] + "/" + prog[1] + " started · Action: 按住 " + keys + " · 手动结束");
        }
    }

    /** 用 Robot 按住一组键中的非移动键（移动键走 MovementController，不走 OS 层） */
    private void pressRobotKeys(List<String> keys) {
        for (String k : keys) {
            Integer code = KeyNames.glfwOf(k);
            if (code == null || KeyNames.isMovementKey(code)) continue;
            if (OsKeySimulator.press(code)) robotHeld.add(k);
        }
    }

    private boolean isHoldLike(Step s) {
        return "hold".equals(s.type) || ("press".equals(s.type) && "hold".equals(s.mode));
    }

    /**
     * 坐标到达判定（确定性容差模型）：每个有效轴要求 |当前-目标| ≤ REACH_TOLERANCE（半格），
     * 全部满足才判到达。
     *
     * <p>为什么这样设计：
     * <ul>
     *   <li>GUI 输入的是 Minecraft 整数坐标（方块坐标语义），玩家坐标是 double，
     *       移动中会出现 -55.98/-56.03 这类值——绝不能用浮点相等比较；</li>
     *   <li>±0.5 等价于 floor(cur+0.5)==target，即"玩家中心进入目标方块所在列"，
     *       对 Block Position 与精确坐标两种理解都稳定，X/Y/Z 策略完全一致；</li>
     *   <li>不依赖"上一 tick→当前 tick"的移动方向推导：玩家从任意方向进入容差带、
     *       甚至起步就在带内（视为已到达）都能正确判定，不会因某个轴本 tick 没动而永远卡住。</li>
     * </ul>
     *
     * <p>Y 轴语义 = 实体脚下坐标（与 GUI"导入当前坐标"一致）：目标 y=73 表示
     * 玩家站在方块顶面 y≈73.0（脚下是 y=72 的方块）。
     *
     * <p>没有任何有效条件时返回 false（未配置的坐标步骤不允许秒过，由卡死告警提醒用户）。
     */
    private boolean posCondMet() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null || curStep == null || curStep.cond == null) return false;
        var pos = c.player.getEntityPos();
        boolean any = false;
        for (var pc : curStep.cond) {
            if (pc == null || isIgnoreOp(pc.op)) continue;
            any = true;
            if (Math.abs(axisVal(pc.axis, pos) - pc.value) > REACH_TOLERANCE + 1e-9) return false;
        }
        return any;
    }

    private static boolean isIgnoreOp(String op) {
        return "ignore".equals(op) || "忽略".equals(op);
    }

    /** 有效（非忽略）坐标条件数量 */
    private int activeCondCount() {
        int n = 0;
        if (curStep != null && curStep.cond != null) {
            for (var pc : curStep.cond) {
                if (pc != null && !isIgnoreOp(pc.op)) n++;
            }
        }
        return n;
    }

    /** 目标坐标文本（调试日志用），如 "x=-56, y=73, z=-239" */
    private String targetText() {
        StringBuilder sb = new StringBuilder();
        if (curStep != null && curStep.cond != null) {
            for (var pc : curStep.cond) {
                if (pc == null || isIgnoreOp(pc.op)) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(pc.axis).append("=").append(pc.value);
            }
        }
        return sb.length() == 0 ? "（未设）" : sb.toString();
    }

    /**
     * 位置步骤看门狗（每 tick）：
     * 1. 采样当前坐标与各轴误差 → 节流 1 秒的调试日志 + HUD 调试行；
     * 2. 卡死检测：最大轴误差超过 STUCK_MS 无任何改善 → 节流告警（不自动停）。
     * 典型卡死：朝向下 A/D 不改变目标轴（如目标只有 X 但玩家面向使横向键沿 Z 移动），
     * 或多轴条件里 Y 目标在纯横向移动下永远不可达。
     */
    private void updatePositionWatchdog(MinecraftClient c, long now) {
        if (curStep == null || !isHoldLike(curStep)) return;
        String ut = curStep.untilType == null ? "time" : curStep.untilType;
        if (!"position".equals(ut) || curStep.cond == null || curStep.cond.isEmpty()) return;
        var pos = c.player.getEntityPos();
        double maxErr = 0;
        StringBuilder err = new StringBuilder();
        for (var pc : curStep.cond) {
            if (pc == null || isIgnoreOp(pc.op)) continue;
            double d = axisVal(pc.axis, pos) - pc.value;
            err.append(pc.axis).append("Δ").append(String.format("%+.2f", d)).append(' ');
            maxErr = Math.max(maxErr, Math.abs(d));
        }
        lastPosText = String.format("pos=(%.2f, %.2f, %.2f) 误差 %s", pos.getX(), pos.getY(), pos.getZ(), err);
        if (maxErr < bestDist - 0.01) {
            bestDist = maxErr;
            lastProgressMs = now;
        }
        if (now - lastPosLogMs >= POS_LOG_INTERVAL_MS) {
            lastPosLogMs = now;
            int[] p = getStepProgress();
            debug("Step " + p[0] + "/" + p[1] + " " + lastPosText + "| reached=" + (maxErr <= REACH_TOLERANCE));
        }
        if (maxErr > REACH_TOLERANCE && now - lastProgressMs > STUCK_MS && now - lastStuckWarnMs > STUCK_WARN_INTERVAL_MS) {
            lastStuckWarnMs = now;
            Feedback.notify("§6[SkyScript] §f可能卡住: 坐标误差 " + Math.round(maxErr * 10) / 10.0
                    + " 格持续不减小（" + err + "）—— 当前朝向下按住的键可能不改变目标轴，或 Y 目标不可达");
        }
    }

    /** 调试日志：debugMode 开启时打到聊天栏（不走 Feedback 的开关与去重，避免被误伤） */
    private void debug(String msg) {
        if (!SkyScriptConfig.get().debugMode) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null) return;
        c.player.sendMessage(net.minecraft.text.Text.literal("§7[Script] §f" + msg), false);
    }

    private static double axisVal(String axis, net.minecraft.util.math.Vec3d pos) {
        return switch (axis == null ? "x" : axis) {
            case "y" -> pos.getY();
            case "z" -> pos.getZ();
            default -> pos.getX();
        };
    }

    private void performTap(Step s) {
        List<String> keys = s.keys == null ? new ArrayList<>() : s.keys;
        implicitTapKeys.clear();
        boolean hasMovement = false;
        for (String k : keys) {
            Integer code = KeyNames.glfwOf(k);
            if (code == null) continue;
            if (KeyNames.isMovementKey(code)) {
                implicitTapKeys.add(k);
                hasMovement = true;
            } else {
                KeySimulator.tapKey(code);
            }
        }
        if (hasMovement) {
            implicitTapUntil = nowMs() + 60;
        }
    }

    private void releaseRobotKeys() {
        for (String k : robotHeld) {
            Integer code = KeyNames.glfwOf(k);
            if (code != null) OsKeySimulator.release(code);
        }
        robotHeld.clear();
    }

    // ---------- 触发键 ----------

    /** 启动诊断：直接从首步按键计算注入方向（A=-1, D=+1），不读尚未写入的控制器状态 */
    private void diagnosticStart() {
        if (curStep != null && SkyScriptConfig.get().master.feedback) {
            String keys = curStep.keys == null ? "[]" : curStep.keys.toString();
            float side = 0;
            if (curStep.keys != null) {
                if (curStep.keys.contains("A")) side -= 1;
                if (curStep.keys.contains("D")) side += 1;
            }
            if (SkyScriptConfig.get().directionSwap) side = -side;
            String dir = side < 0 ? "左(x=+1)" : side > 0 ? "右(x=-1)" : "无";
            Feedback.notify("§7[SkyScript] §f诊断: 首步=" + keys + " 注入" + dir);
        }
    }

    /** 进入世界时重置触发键历史状态，防止跨世界残留误触发 */
    public void resetTriggers() {
        prevTrigger.clear();
        KeyEvents.clear();
    }

    /**
     * 事件驱动触发：由 KeyEventCatcher 捕获真实按键抬起事件（Keyboard.onKey），
     * 语义与 AHK 的 key-up 触发一致，快速点击也不会漏检。
     */
    private void pollTriggers(MinecraftClient client) {
        // 未开启（F8 未 arm）时触发键完全不响应 —— 恢复 AHK「#HotIf FeatureOn()」语义：
        // 进游戏默认/关闭总开关后，A/D 只是普通移动键，怎么按都不会误启动脚本。
        if (!armed) return;
        // 打开界面时不响应触发键（冻结期间保持静默）
        if (client.currentScreen != null) return;
        List<String> manual = SkyScriptConfig.get().triggerKeys;
        List<String> triggers;
        if (manual != null && !manual.isEmpty()) {
            triggers = manual;
        } else {
            // 自动：活动方案里用到的所有按键都能启动（首步是 W 就按 W，A/D 交替就 A、D 都能按）
            Script active = SkyScriptConfig.getActiveScript();
            triggers = new ArrayList<>();
            if (active != null && active.steps != null) {
                collectKeys(active.steps, triggers);
            }
            if (triggers.isEmpty()) return;
        }
        KeyEvents.cleanup();
        for (String name : triggers) {
            Integer code = KeyNames.glfwOf(name);
            if (code == null) continue;
            // 仅在"短按（点击）松开"时触发；长按（如走路）不会启动/切换脚本
            if (!KeyEvents.consumeClick(code, KeyEvents.MAX_CLICK_MS)) continue;

            if (!isRunning()) {
                Script active = SkyScriptConfig.getActiveScript();
                if (active == null) {
                    Feedback.notify("§6[SkyScript] §f没有活动方案: 按 §eO§f 选「活动」");
                    continue;
                }
                if (active.steps == null || active.steps.isEmpty()) {
                    Feedback.notify("§6[SkyScript] §f这个方案还没有动作，先编辑加动作");
                    continue;
                }
                // 先记录触发键，再 start()（start 内部调 stop() 会把 lateralBias 重置回 "A"，
                // 所以必须在 start 之后重新设置 bias 再重映射，否则"按 D 启动却往左走"）
                String startKey = KeyNames.isLateralKey(name) ? name : null;
                debug("触发键 " + name + " 短按 → 启动方案");
                start(active);
                if (startKey != null) {
                    lateralBias = startKey;
                    applyLateralBias(startKey);
                }
                Feedback.notify("§a[SkyScript] §f触发启动: §e" + active.name + " §7(按 " + name + " 开始)");
                diagnosticStart();
            } else {
                handleRunTrigger(name);
            }
        }
    }

    /** 收集方案里用到的所有按键（含循环体），去重 */
    private static void collectKeys(List<Step> steps, List<String> out) {
        if (steps == null) return;
        for (Step s : steps) {
            if (s == null) continue;
            if (s.keys != null) {
                for (String k : s.keys) {
                    if (k != null && !k.isEmpty() && !out.contains(k)) out.add(k);
                }
            }
            if ("loop".equals(s.type) && s.body != null) collectKeys(s.body, out);
        }
    }

    /** 取动作的第一个有效按键（hold/press 的首键） */
    private static String firstKeyOf(Step s) {
        if (s == null || s.keys == null) return null;
        for (String k : s.keys) {
            if (k != null && !k.isEmpty()) return k;
        }
        return null;
    }

    private void handleRunTrigger(String name) {
        if (lateralKey == null) return;
        if (name.equals(lateralKey)) {
            if ("stop".equals(SkyScriptConfig.get().currentKeySemantics)) {
                stop("触发键 " + name + " 短按（运行中再按当前方向键=停止）");
                Feedback.notify("§c[SkyScript] §f已停止");
            } else {
                debug("触发键 " + name + " 短按（语义=忽略，不停止）");
            }
            return;
        }
        if (!KeyNames.isLateralKey(name)) return;
        // 切换方向：重算 bias，使当前列变成新键
        lateralBias = name;
        applyLateralBias(name);
        debug("触发键 " + name + " 短按 → 切换横向方向（重映射所有横向步骤）");
        Feedback.notify("§7[SkyScript] §f切换到 §e" + name);
        // 重置当前步骤计时
        phaseStartMs = nowMs();
        if (curStep != null && "time".equals(curStep.untilType)) {
            holdUntilMs = nowMs() + Math.max(0, curStep.ms < 0 ? 120000 : curStep.ms);
        }
    }

    /**
     * 按"位置交替"规则重映射脚本里所有横向 hold 步骤的键。
     * 第 i 个横向步骤：i 为偶数 → bias；奇数 → 另一个方向键。
     */
    private void applyLateralBias(String bias) {
        if (script == null) return;
        final int[] i = {0};
        for (Step s : script.steps) remapStep(s, bias, i);
        // 刷新当前步骤的横向键
        lateralKey = null;
        if (curStep != null && curStep.keys != null && curStep.keys.size() == 1) {
            String k = curStep.keys.get(0);
            if ("A".equals(k) || "D".equals(k)) lateralKey = k;
        }
    }

    private void remapStep(Step s, String bias, int[] counter) {
        if (s == null) return;
        if ("hold".equals(s.type) || ("press".equals(s.type) && "hold".equals(s.mode))) {
            if (s.keys != null && s.keys.size() == 1) {
                String k = s.keys.get(0);
                if ("A".equals(k) || "D".equals(k)) {
                    boolean even = counter[0] % 2 == 0;
                    s.keys.set(0, even == "A".equals(bias) ? "A" : "D");
                    counter[0]++;
                }
            }
        }
        if ("loop".equals(s.type) && s.body != null) {
            for (Step b : s.body) remapStep(b, bias, counter);
        }
    }

    private long nowMs() {
        return System.currentTimeMillis();
    }
}

package dev.skyscript.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.skyscript.config.SkyScriptConfig;
import dev.skyscript.input.KeyNames;
import dev.skyscript.input.KeySimulator;
import dev.skyscript.input.MovementController;
import dev.skyscript.input.OsKeySimulator;
import dev.skyscript.script.Script;
import dev.skyscript.script.Step;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

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
 */
public final class ScriptEngine {

    public static final ScriptEngine INSTANCE = new ScriptEngine();

    private static final Gson GSON = new GsonBuilder().create();

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
    private boolean running;
    private boolean frozen;
    private long frozenSince;
    private long phaseStartMs;
    private long holdUntilMs;
    private String lateralBias = "A";
    private String lateralKey;                 // 当前步骤的横向键（无则 null）
    private final Set<String> robotHeld = new HashSet<>();
    private final Map<String, Boolean> prevTrigger = new HashMap<>();
    private long implicitTapUntil;
    private final Set<String> implicitTapKeys = new HashSet<>();

    private ScriptEngine() {
    }

    // ---------- 状态查询（HUD 用） ----------

    public boolean isRunning() {
        return running;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Script getScript() {
        return script;
    }

    public Step getCurStep() {
        return curStep;
    }

    public String getStateText() {
        if (!running) return "空闲";
        return frozen ? "暂停" : "运行";
    }

    public String getStepSummary() {
        return curStep == null ? "—" : curStep.summary();
    }

    public int getTimeLeftSeconds() {
        if (!running || curStep == null || !"time".equals(curStep.untilType)) return -1;
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
        script = GSON.fromJson(GSON.toJson(original), Script.class);
        stack.clear();
        stack.push(new Frame(script.steps, script.loop));
        running = true;
        frozen = false;
        enterStep(stack.peek(), 0);
    }

    public void stop() {
        running = false;
        frozen = false;
        script = null;
        curStep = null;
        stack.clear();
        releaseRobotKeys();
        MovementController.clear();
        implicitTapKeys.clear();
        lateralBias = "A";
        lateralKey = null;
    }

    // ---------- 每 tick 驱动 ----------

    public void tick(MinecraftClient client) {
        pollTriggers(client);
        if (!running) return;
        if (client.player == null || client.getNetworkHandler() == null) {
            stop();
            return;
        }
        if (client.player.isDead()) {
            stop();
            return;
        }

        boolean screenOpen = client.currentScreen != null;
        if (screenOpen) {
            if (!frozen) {
                frozen = true;
                frozenSince = nowMs();
                MovementController.clear();
            }
            return;
        }
        if (frozen) {
            long d = nowMs() - frozenSince;
            phaseStartMs += d;
            holdUntilMs += d;
            frozen = false;
        }

        long now = nowMs();
        advanceIfDue(now);

        // 应用移动输入
        if (running && curStep != null) {
            if (now < implicitTapUntil) {
                MovementController.setDesired(implicitTapKeys);
            } else if (isHoldLike(curStep)) {
                MovementController.setDesired(curStep.keys);
            } else {
                MovementController.clear();
            }
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
            stop();
            return;
        }
        f.index++;
        if (f.index < f.steps.size()) {
            enterStep(f, f.index);
            return;
        }
        // 本帧跑完
        stack.pop();
        if (stack.isEmpty()) {
            // 脚本级帧完成：无限循环(loop=0)则重来，否则整份结束
            if (f.remaining == 0) {
                stack.push(new Frame(script.steps, script.loop));
                enterStep(stack.peek(), 0);
            } else if (f.remaining > 1) {
                stack.push(new Frame(script.steps, f.remaining - 1));
                enterStep(stack.peek(), 0);
            } else {
                stop();
            }
            return;
        }
        // 内层帧完成 → 父帧继续
        advance();
    }

    private void enterStep(Frame f, int index) {
        f.index = index;
        curStep = f.steps.get(index);
        phaseStartMs = nowMs();
        switch (curStep.type) {
            case "wait" -> holdUntilMs = nowMs() + Math.max(0, curStep.ms < 0 ? 0 : curStep.ms);
            case "hold" -> setupHold();
            case "press" -> {
                if ("hold".equals(curStep.mode)) {
                    setupHold();
                } else {
                    performTap(curStep);
                    advance();
                }
            }
            case "command" -> {
                MinecraftClient c = MinecraftClient.getInstance();
                if (c.getNetworkHandler() != null) {
                    String cmd = curStep.value == null ? "" : curStep.value.trim();
                    if (!cmd.isEmpty()) {
                        if (cmd.startsWith("/")) cmd = cmd.substring(1);
                        c.getNetworkHandler().sendChatCommand(cmd);
                    }
                }
                advance();
            }
            case "loop" -> {
                int t = Math.max(1, curStep.times);
                if (curStep.body.isEmpty()) {
                    advance(); // 空循环体直接跳过
                } else {
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
        // 非移动键 → Robot 按住
        for (String k : keys) {
            Integer code = KeyNames.glfwOf(k);
            if (code == null || KeyNames.isMovementKey(code)) continue;
            if (OsKeySimulator.press(code)) robotHeld.add(k);
        }
        String ut = curStep.untilType == null ? "time" : curStep.untilType;
        if ("time".equals(ut)) {
            holdUntilMs = nowMs() + Math.max(0, curStep.ms < 0 ? 120000 : curStep.ms);
        } else {
            holdUntilMs = Long.MAX_VALUE;
        }
    }

    private boolean isHoldLike(Step s) {
        return "hold".equals(s.type) || ("press".equals(s.type) && "hold".equals(s.mode));
    }

    private boolean posCondMet() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null || curStep == null || curStep.cond == null) return false;
        var pos = c.player.getEntityPos();
        for (var pc : curStep.cond) {
            double v = switch (pc.axis == null ? "x" : pc.axis) {
                case "y" -> pos.getY();
                case "z" -> pos.getZ();
                default -> pos.getX();
            };
            if (!pc.test(v)) return false;
        }
        return true;
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

    private void pollTriggers(MinecraftClient client) {
        List<String> triggers = SkyScriptConfig.get().triggerKeys;
        if (triggers == null || triggers.isEmpty()) return;
        var win = client.getWindow();
        if (win == null) return;
        for (String name : triggers) {
            Integer code = KeyNames.glfwOf(name);
            if (code == null) continue;
            boolean pressed = InputUtil.isKeyPressed(win, code);
            if (!prevTrigger.containsKey(name)) {
                prevTrigger.put(name, pressed);
                continue;
            }
            boolean prev = prevTrigger.get(name);
            prevTrigger.put(name, pressed);
            if (pressed || prev) continue; // 需要"按下 → 抬起"沿

            if (!running) {
                Script active = SkyScriptConfig.getActiveScript();
                if (active == null) continue;
                if (KeyNames.isLateralKey(name)) lateralBias = name;
                start(active);
                if (KeyNames.isLateralKey(name)) applyLateralBias(lateralBias);
            } else {
                handleRunTrigger(name);
            }
        }
    }

    private void handleRunTrigger(String name) {
        if (lateralKey == null) return;
        if (name.equals(lateralKey)) {
            if ("stop".equals(SkyScriptConfig.get().currentKeySemantics)) stop();
            return;
        }
        if (!KeyNames.isLateralKey(name)) return;
        // 切换方向：重算 bias，使当前列变成新键
        lateralBias = name;
        applyLateralBias(name);
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

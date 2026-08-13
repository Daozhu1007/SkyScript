#Requires AutoHotkey v2.0
#SingleInstance Force

; ============================================================
;  Skyblock 自动种田:A/D 交替循环长按
;  · 单击 A → 按住 A 走 120 秒 → 停 0.5 秒 → 自动换 D → 交替,共 5 列自动停
;    (A-D-A-D-A;先按 D 则 D-A-D-A-D)
;  · 单击 D → 同理,但从 D 开始
;  · 再按一次当前正在用的键 → 停止
;  · F8 → 开启 / 关闭整个功能
;  · 想调时间:改下面 holdMs(一列)和 pauseMs(列间暂停)
; ============================================================

enabled  := false        ; F8 总开关
cycleKey := ""           ; 当前方向键;"" = 停止
phase    := "hold"       ; 当前阶段:hold(按住走) / pause(列间暂停)
phaseEnd := 0            ; 当前阶段结束的时间点(毫秒)
holdMs   := 120000       ; 一列时长:120 秒
pauseMs  := 500          ; 列间暂停:0.5 秒(想改 1 秒就填 1000)
segCount := 0            ; 已走完的列数
maxSegs  := 5            ; 本趟总共走几列,走完自动停

OnExit(ReleaseOnExit)
SetTimer(Driver, 50)     ; 每 50ms 检查一次,负责阶段切换和方向交替

; ---------------- 按键 ----------------

F8:: {
    global enabled
    enabled := !enabled
    if !enabled
        StopAll()
    TrayTip("自动种田: " (enabled ? "已开启 (单击 A / D 开始)" : "已关闭"))
}

; 功能开启时才接管 A / D;关闭时 A / D 就是普通按键
#HotIf FeatureOn()
A up:: ToggleCycle("A")
D up:: ToggleCycle("D")
#HotIf

FeatureOn() {
    global enabled
    return enabled
}

; 单击 A 或 D:开始循环 / 切换 / 停止
ToggleCycle(key) {
    global cycleKey, phase, phaseEnd, holdMs, segCount
    if (cycleKey == key) {              ; 再按一次同一个键 → 停止
        StopAll()
        return
    }
    StopAll()                           ; 先松开之前的方向
    segCount := 0                       ; 重新计数
    cycleKey := key
    phase    := "hold"
    phaseEnd := A_TickCount + holdMs
    Send("{" key " down}")              ; 开始按住
}

StopAll() {
    global cycleKey
    if cycleKey {
        Send("{" cycleKey " up}")       ; 松开
        cycleKey := ""
    }
}

; 每 50ms 跑一次:到点就切换"按住 ↔ 暂停",方向自动 A/D 交替
Driver() {
    global cycleKey, phase, phaseEnd, holdMs, pauseMs, segCount, maxSegs
    if !cycleKey
        return
    if (A_TickCount < phaseEnd)
        return
    if (phase == "hold") {
        ; --- if:一列走完 → 计数 +1 ---
        segCount += 1
        if (segCount >= maxSegs) {      ; --- if:走满列数 → 整趟结束,自动停 ---
            StopAll()
            segCount := 0
            TrayTip("本趟 " maxSegs " 列走完,已自动停止 (再点 A / D 重来)")
            return
        }
        ; --- 没走满 → 松开,进入列间暂停 ---
        Send("{" cycleKey " up}")
        phase    := "pause"
        phaseEnd := A_TickCount + pauseMs
    } else {
        ; --- if:暂停结束 → 换个方向,继续走 ---
        if (cycleKey == "A")
            cycleKey := "D"
        else
            cycleKey := "A"
        Send("{" cycleKey " down}")
        phase    := "hold"
        phaseEnd := A_TickCount + holdMs
    }
}

; 脚本退出时松开按键,防止卡键
ReleaseOnExit(*) {
    global cycleKey
    if cycleKey
        Send("{" cycleKey " up}")
}

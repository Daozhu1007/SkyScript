# SkyScript — 设计文档

> 一个可编程的 Skyblock 种田自动化 Fabric Mod（纯客户端）。
> 从 `AutoKey.ahk`（AHK 脚本）进化而来：不再只是"按 120 秒换方向"，而是游戏内可编程的自动化框架。

- **项目名**：SkyScript
- **项目根目录**：`D:\Code\SkyScript`
- **mod id**：`sky_script`
- **目标版本**：Minecraft 1.21.11（Fabric）
- **环境**：纯客户端（`"environment": "client"`），服务端无需安装
- **状态**：✅ **v0.1.0 已实现并构建成功**（`build/libs/skyscript-0.1.0.jar`）。实现过程发现 1.21.11 输入系统大重构（`Input.playerInput`/`movementVector`、`KeyInput`/`MouseInput`/`Click` 事件、`Keyboard.onKey` 私有化、`Entity.getEntityPos`、`KeyBinding.Category`、Loom 直写 mixin 无需 refmap），全部已适配。待实测：Lunar 加载、攻击模式切换、坐标触发实机调参。

---

## 1. 功能总览

| 能力 | 说明 |
|---|---|
| 脚本引擎 | JSON 步骤式方案：hold / wait / press / command / loop |
| 触发条件 | 时间计时、绝对坐标（轴+比较符+值）、手动 |
| 循环语义 | 方案级 `loop`（默认 0 = 无限，可配有限）+ 嵌套 loop 步骤 |
| 输入注入 | 移动键直接注入（mixin），任意键/鼠标走事件管线，外部目标走 OS 级模拟 |
| F8 总控 | 一键联动：脚本启停 + 攻击/摧毁模式切换 + 外部热键触发 + HUD |
| HUD | Lunar 风格、模板文本可自定义、静默模式 |
| 游戏内编辑器 | 方案列表 → 步骤列表 → 步骤编辑 三个 Screen |
| 配置 | `config/sky_script/settings.json` + `scripts/*.json` |

---

## 2. 架构模块

```
SkyScript
├─ core/      脚本模型(JSON) + 运行时状态机 + 步骤执行器
├─ input/     移动注入(mixin) + 按键/鼠标事件注入 + OS级模拟(Robot/JNA)
├─ hud/       HUD 渲染器(模板文本/占位符/静默模式)
├─ screen/    游戏内编辑器 GUI(方案列表/步骤列表/步骤编辑)
├─ config/    JSON 读写(config/sky_script/ 目录)
├─ bind/      键位系统(所有键可绑)
└─ mixins/    KeyboardInput#tick 等
```

---

## 3. 脚本 DSL 规格

### 3.1 方案结构（示例）

```json
{
  "name": "自动种田-南瓜田",
  "loop": 0,
  "steps": [
    {
      "type": "loop",
      "times": 5,
      "body": [
        {
          "type": "hold",
          "keys": ["A"],
          "untilType": "position",
          "cond": [ { "axis": "x", "op": "<=", "value": 100.5 } ]
        },
        { "type": "wait", "ms": 500 },
        {
          "type": "hold",
          "keys": ["D"],
          "untilType": "time",
          "ms": 120000
        },
        { "type": "wait", "ms": 500 }
      ]
    },
    { "type": "command", "value": "/home" },
    { "type": "press", "keys": ["R"], "mode": "tap" }
  ]
}
```

> 注：实现时将 DESIGN 早期版本的嵌套 `until` 对象简化为平铺字段
> `untilType`（time/position/manual）+ `ms` + `cond`，语义不变。

### 3.2 步骤类型（v1）

| 类型 | 字段 | 说明 |
|---|---|---|
| `hold` | `keys` + `until` | 按住一组键直到条件满足。`until.type`：`time`(ms) / `position`(cond) / `manual` |
| `wait` | `ms` | 纯等待 |
| `press` | `keys` + `mode` | 点按/长按任意键，`mode`: `tap` / `hold` |
| `command` | `value` | 执行指令，走 `sendChatCommand`，不进聊天框、不弹输入界面 |
| `loop` | `times` + `body` | 嵌套循环，body 重复 N 次 |

### 3.3 位置触发语义

- 条件 = `轴 + 比较符 + 值` 列表（多条 = 同时满足），每 tick 判定。
- **只用绝对坐标**（Hypixel 不隐藏 F3 坐标），不做相对偏移。
- 方向感知由用户写对比较符：按 A 往 -X 走 → 写 `x <= 100.5`。

### 3.4 循环/结束语义

- 方案级 `loop`：`0` = 无限循环直到手动停（**默认**）；`N` = 整份跑 N 轮后自动停。
- `loop` 步骤保留有限次循环能力（如 5 列）。

---

## 4. 运行时状态机

- 每 tick 驱动：步骤执行器推进 hold/pause 切换、条件判定、循环计数。
- **停止条件**：F8 / 手动停 / 断线 / 死亡。
- **打开界面**（背包/聊天/合成/暂停）= **冻结计时**：不移动、不消耗列时间，关掉界面继续走。
- **按键语义（全局设置）**：
  - `运行时按当前键`：`stop`（方案①：再按当前键=停止）/ `ignore`（方案②：无操作）
  - `运行时按其他键`：切换到该键并重置列计数（原 AHK 行为）

---

## 5. 输入层（三层）

| 层次 | 机制 | 用途 | 局限 |
|---|---|---|---|
| 移动注入 | mixin 覆写 `KeyboardInput#tick` 的 movement 字段 | A/S/D/W/空格等移动键 | 只对移动有效 |
| 游戏内事件注入 | 调 `client.keyboard.onKey(...)` / `client.mouse.onMouseButton(...)` | 任意键/鼠标：背包、热键栏、走 MC 键位系统的 mod 功能 | 走不到 GLFW 层注册的第三方监听 |
| OS 级模拟 | Java Robot / JNA SendInput | 任何收不到游戏内事件的目标 | 需要窗口焦点 |

---

## 6. F8 总控联动

按下 F8 执行**联动动作序列**（每项可开关）：

1. 脚本启/停
2. 攻击/摧毁模式切换（`GameOptions` 里的 AttackMode 选项，字段名实现时确认，必要时 mixin accessor；切换模式下需注入一次左键"锁定"，停止时再注入一次解锁并恢复 HOLD）
3. **外部热键触发：默认 PgDn**（用户 Lunar 内置"锁定鼠标"热键），触发方式每键可配：`inject`（游戏内注入）/ `os`（OS 级模拟）
4. HUD 开关

> 环境提示：切纯 Fabric 保底环境时，建议把 SkyHanni 的锁定鼠标热键也绑成 PgDn，两个环境手感一致，配置零改动。

---

## 7. HUD

- Lunar 风格：屏幕角落小字，预设四角 + 偏移，背景/透明度/字号可调。
- **模板文本可自定义**，占位符：`{state} {script} {step} {col}/{total} {timeLeft} {attackMode}`，默认中文文案。
- **静默模式**：完全不渲染、不弹任何提示。
- 不用 Action Bar（避免影响 UI）。

---

## 8. 配置存储

```
config/sky_script/
├─ settings.json      # 全局：按键语义、HUD 模板、F8 联动动作、外部热键配置
└─ scripts/*.json     # 每方案一份
```

---

## 9. 游戏内编辑器

三个 Screen（工程量的最大头，预计 800~1500 行，无技术风险）：

1. **方案列表**：查看/新建/删除/启用方案
2. **步骤列表**：查看/增删/排序方案内步骤
3. **步骤编辑**：类型下拉、坐标/时间输入框、按键录制器

---

## 10. 环境与版本策略

- **标准纯客户端 Fabric mod**，兼容任何 Fabric 环境。
- **M0 前置实测**：Lunar Client 1.21.11 能否加载我们的 jar。
  - 路 A：能 → 环境不变，继续用 Lunar。
  - 路 B（保底）：纯 Fabric = Fabric Loader + Fabric API + Modrinth 版 SkyHanni + SkyScript。
- **版本策略**：1.21.11 单版本起步 + 版本隔离纪律（版本相关代码集中在 `input/` 与 `mixins/` 少量文件，记录移植清单）；26.1/26.2（年份版本号体系）发布后，若需双版本并行再引入 Stonecutter。

---

## 11. 里程碑

| 阶段 | 内容 | 预估 |
|---|---|---|
| M0 | ~~前置验证~~ 已延后：与 M1 产物合并实测（Lunar 加载 + AttackMode 字段确认顺带完成） | 0 |
| M1 | 骨架 + 键位框架 + 移动注入 mixin + HUD + 时间型步骤 | 2-3 天 |
| M2 | 引擎完整：position/command/press/loop + 全停止条件 + 按键语义设置 | 2-3 天 |
| M3 | 编辑器 GUI（三个 Screen） | 3-5 天 |
| M4 | F8 联动：AttackMode 读写 + 左键注入 + 外部热键 + Lunar 实测调优 | 2-3 天 |
| M5 | 版本隔离整理 + 移植清单文档 | 1-2 天 |

总计约 2~3 周，代码量预估 2500~4000 行。

---

## 12. 风险清单

1. **Lunar 1.21.11 Fabric 加载兼容性**（最高优先，M0 实测；保底方案已备）
2. **Lunar 内置功能能否被游戏内注入触发**（备选 OS 级模拟）
3. **服务器反宏规则**：纯客户端 mod 不增加检测面，但自动化行为本身取决于服务器规则
4. **AttackMode 字段名随版本变化**（mixin accessor 兜底）

---

## 13. 命名与发布

- 显示名：SkyScript
- mod id：`sky_script`
- 发布 Modrinth 前需查重名

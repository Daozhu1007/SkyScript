# SkyScript

> 可编程的 Skyblock 种田自动化 Fabric Mod（纯客户端，Minecraft 1.21.11）

从 `AutoKey.ahk`（AHK 脚本）进化而来的游戏内自动化框架：JSON 步骤式脚本（时间 / 坐标 / 手动触发）、F8 一键总控（脚本 + 攻击/摧毁切换 + 外部热键联动 + HUD）、游戏内编辑器、Lunar 风格可自定义 HUD。

## 目录

| 文件 | 说明 |
|---|---|
| `DESIGN.md` | 设计定稿（架构 / DSL / 状态机 / 输入层 / 里程碑 / 风险） |
| `AutoKey.ahk` | 原脚本（历史参考） |
| `examples/` | 示例脚本 JSON |
| `build/libs/skyscript-0.1.0.jar` | **成品 mod（构建产物）** |

## 安装

1. 需要 [Fabric Loader](https://fabricmc.net/use/)（0.17+）和 [Fabric API](https://modrinth.com/mod/fabric-api)（1.21.11 版本），客户端为 1.21.11；
2. 把 `build/libs/skyscript-0.1.0.jar` 放进 `.minecraft/mods/`；
3. （可选）Lunar Client 若支持 Fabric mod 加载，可直接放入；否则用纯 Fabric 客户端 + Modrinth 版 SkyHanni。

## 快速上手

1. 进游戏后按 **H** 打开脚本编辑器；
2. 「新建方案」→ 默认生成 AHK 风格的 A/D 交替模板（各 120 秒，间隔 0.5 秒）；
3. 「完成」保存，回到游戏；
4. 按 **F8** 一键开跑：脚本开始 + 攻击模式切为"切换"（点一下自动连收割）+ 触发外部热键（默认 PgDn，即你绑的锁定鼠标）；
5. 再按 **F8** 停止：脚本停 + 攻击模式恢复"长按" + 再触发一次 PgDn。

也可以不按 F8，直接**单击 A 或 D** 启动（抬起触发），运行中按另一个方向键切换方向、再按当前键停止（可在设置里改为"忽略"）。

## 快捷键

| 键 | 功能 |
|---|---|
| F8 | 总控：脚本启停 + 攻击/摧毁模式切换 + 外部热键 + HUD 开关（均可配置） |
| H | 打开脚本编辑器 |
| A / D | 空闲时启动方案（抬起触发）；运行中切换方向 / 停止（语义可配置） |

## 脚本格式（config/sky_script/scripts/*.json）

```json
{
  "name": "自动种田-南瓜田",
  "loop": 0,
  "steps": [
    {
      "type": "loop",
      "times": 5,
      "body": [
        { "type": "hold", "keys": ["A"], "untilType": "position",
          "cond": [ { "axis": "x", "op": "<=", "value": 100.5 } ] },
        { "type": "wait", "ms": 500 },
        { "type": "hold", "keys": ["D"], "untilType": "time", "ms": 120000 },
        { "type": "wait", "ms": 500 }
      ]
    },
    { "type": "command", "value": "/home" },
    { "type": "press", "keys": ["R"], "mode": "tap" }
  ]
}
```

| 步骤类型 | 字段 | 说明 |
|---|---|---|
| `hold` | `keys` + `untilType` | 按住一组键直到条件满足；`untilType`: `time`(配 `ms`) / `position`(配 `cond` 轴+比较符+值) / `manual` |
| `wait` | `ms` | 等待毫秒数 |
| `press` | `keys` + `mode` | `tap` 点按 / `hold` 按住（hold 模式同上配 `untilType`） |
| `command` | `value` | 发送指令，如 `/home`（不进聊天框） |
| `loop` | `times` + `body` | 循环体重复 N 次 |

- 方案级 `loop`：`0` = 无限循环直到手动停（默认），`N` = 整份跑 N 轮后停；
- 键名写法：`A` `D` `W` `S` `SPACE` `LSHIFT` `F1`~`F24` `PGDN` `PGUP` `ENTER` `ESC` 等（编辑器里可"录制按键"）；
- **方向交替**：脚本里横向 hold 步骤（单个 A/D 键）按位置交替规则映射——用 A 启动就是 A,D,A,D…，用 D 启动就是 D,A,D,A…，运行中切换也遵循此规则（同原 AHK 行为）。

## 全局设置（config/sky_script/settings.json）

```json
{
  "currentKeySemantics": "ignore",        // 运行中按当前方向键: stop=停止 / ignore=无操作
  "otherKeySemantics": "switch",          // 运行中按另一个方向键: switch=切换
  "triggerKeys": ["A", "D"],              // 空闲时启动脚本的键
  "activeScript": "自动种田-南瓜田",        // F8/触发键启动的方案
  "hud": {
    "enabled": true, "silent": false,     // silent=静默模式(完全不渲染)
    "template": "SkyScript §7{state} §f{script} §7{step} §f{timeLeft}s §7{attackMode}",
    "pos": "top-left", "x": 4, "y": 4, "background": true, "scale": 1.0
  },
  "master": {
    "toggleScript": true, "toggleAttackMode": true, "toggleHud": true,
    "externalKeys": [ { "key": "PGDN", "method": "inject" } ]
  }
}
```

HUD 占位符：`{state}` `{script}` `{step}` `{col}` `{total}` `{timeLeft}` `{attackMode}`。
外部热键 `method`：`inject`（游戏内事件注入，走 MC 键位系统，如 SkyHanni）/ `os`（OS 级模拟，给收不到游戏内事件的功能，如 Lunar 内置热键，需要窗口焦点）。

## 行为细节

- **界面冻结**：打开背包/聊天/任意界面时脚本暂停计时且不移动，关掉界面继续；
- **自动停止**：断线、死亡、方案跑完（有限 loop）自动停；
- **位置触发**：绝对坐标（轴 + 比较符 + 值），每 tick 判定；
- **移动注入**：直接覆写 `Input.playerInput`/`movementVector`（mixin），等价长按按键，不依赖窗口焦点。

## 构建

```bash
gradlew build          # 产物在 build/libs/
```

工具链：JDK 21+、Gradle 9.5+（Loom 1.17.19 要求）、Fabric API 0.141.6+1.21.11、Yarn 1.21.11+build.6。

## 推送到 GitHub（网络受限环境）

本机网络环境过滤了 `github.com` 的 HTTPS（连接被重置），普通 `git push` 无法直连。
已配置两种替代通道，**日常推送用脚本即可**：

```powershell
# 方式一（推荐，走 GitHub git-data API，经 api.github.com 可达）
git add -A && git commit -m "你的提交信息"
pwsh -File .toolchain\push-via-api.ps1

# 方式二（SSH over 443，~/.ssh/config 已配置 ssh.github.com:443）
# 需要先把本机 SSH 公钥注册到 GitHub 账号：gh auth refresh -h github.com -s admin:public_key && gh ssh-key add ~/.ssh/id_ed25519.pub
git push origin main
```

> 注意：`.toolchain\` 已被 .gitignore 排除（存放 Gradle 发行版与推送脚本），不会进仓库。
> 部署密钥已注册到仓库（只对 SkyScript 生效），待账号级 SSH 密钥就绪后可直接走方式二。

## 风险提示

- 自动化挂机行为是否合规取决于服务器规则（Hypixel 等明令禁止 macro）；
- 本 mod 纯客户端，服务端无需安装，也不修改任何游戏文件。

## 状态

✅ **v0.1.0 已构建完成**（`build/libs/skyscript-0.1.0.jar`）。待实测：Lunar 1.21.11 加载、攻击模式切换、坐标触发实机调参。

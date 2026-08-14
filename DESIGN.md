# SkyScript — Design Specification

> A programmable SkyBlock farming automation Fabric mod (client-side only).
> Evolved from `AutoKey.ahk` (an AutoHotkey script): no longer merely "switch direction every 120 seconds", but a full in-game programmable automation framework.

- **Project name**: SkyScript
- **Project root**: `D:\Code\SkyScript`
- **Mod id**: `sky_script`
- **Target version**: Minecraft 1.21.11 (Fabric)
- **Environment**: client-only (`"environment": "client"`); no server-side installation required
- **Status**: ✅ **v0.1.1 implemented and built** (`build/libs/skyscript-0.1.1.jar`). Implementation uncovered the 1.21.11 input-system refactor (`Input.playerInput`/`movementVector`, `KeyInput`/`MouseInput`/`Click` events, privatization of `Keyboard.onKey`, `Entity.getEntityPos`, `KeyBinding.Category`, Loom's direct mixin rewriting without refmaps) — all adapted. Pending field testing: Lunar Client loading, attack-mode switching, coordinate-trigger tuning.

---

## 1. Feature Overview

| Capability | Description |
|---|---|
| Script engine | JSON step-based scripts: hold / wait / press / command / loop |
| Triggers | Time-based, absolute coordinates (axis + operator + value), manual |
| Loop semantics | Script-level `loop` (default 0 = infinite, finite configurable) + nested `loop` steps |
| Input injection | Movement keys injected directly (mixin); arbitrary keys/mouse via event pipeline; external targets via OS-level simulation |
| F8 master control | One-key orchestration: script toggle + attack/destroy mode switch + external hotkey trigger + HUD |
| HUD | Lunar style, customizable template text, silent mode |
| In-game editor | Three screens: script list → step list → step edit |
| Configuration | `config/sky_script/settings.json` + `scripts/*.json` |

---

## 2. Architecture

```
SkyScript
├─ engine/     Script model (JSON) + runtime state machine + step executor
├─ input/      Movement injection (mixin) + key/mouse event injection + OS-level simulation (Robot)
├─ hud/        HUD renderer (template text / placeholders / silent mode)
├─ screen/     In-game editor GUI (script list / step list / step edit) + settings screen
├─ config/     JSON I/O (config/sky_script/ directory)
└─ mixins/     KeyboardInput#tick, Keyboard/Mouse/Input accessors
```

---

## 3. Script DSL Specification

### 3.1 Script Structure (Example)

```json
{
  "name": "pumpkin-farm",
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

> Note: the nested `until` object from the early design was flattened during implementation into `untilType` (time/position/manual) + `ms` + `cond`; semantics unchanged.

### 3.2 Step Types (v1)

| Type | Fields | Description |
|---|---|---|
| `hold` | `keys` + `untilType` | Hold a set of keys until a condition is met. `untilType`: `time` (ms) / `position` (cond) / `manual` |
| `wait` | `ms` | Pure wait |
| `press` | `keys` + `mode` | Tap or hold arbitrary keys; `mode`: `tap` / `hold` |
| `command` | `value` | Execute a command via `sendChatCommand`; bypasses the chat UI |
| `loop` | `times` + `body` | Nested loop; repeats body N times |

### 3.3 Position Trigger Semantics

- Condition = list of `axis + operator + value` (multiple entries = AND), evaluated every tick.
- **Absolute coordinates only** (Hypixel does not hide F3 coordinates); no relative offsets.
- Direction awareness is expressed by the operator: moving with A toward −X → write `x <= 100.5`.

### 3.4 Loop / Termination Semantics

- Script-level `loop`: `0` = infinite until stopped manually (**default**); `N` = stop automatically after N full rounds.
- `loop` steps retain finite repetition (e.g. 5 rows).

---

## 4. Runtime State Machine

- Tick-driven: the step executor advances hold/pause transitions, evaluates conditions, and counts loop iterations.
- **Stop conditions**: F8 / manual stop / disconnect / death.
- **Screen open** (inventory/chat/crafting/pause) = **timer freeze**: no movement, no time consumption; resumes when the screen closes.
- **Key semantics (global setting)**:
  - `currentKeySemantics` while running: `stop` (pressing the current direction key stops) / `ignore` (no-op)
  - pressing the other direction key: switch to that key and reset column timing (original AHK behavior)

---

## 5. Input Layers (Three-Tier)

| Layer | Mechanism | Use | Limitation |
|---|---|---|---|
| Movement injection | Mixin overrides movement fields after `KeyboardInput#tick` | Movement keys (A/S/D/W/SPACE) | Movement only |
| In-game event injection | Invokes `Keyboard.onKey(...)` / `Mouse.onMouseButton(...)` via mixin accessors | Arbitrary keys/mouse: inventory, hotbar, MC-keybind-based mod features | Cannot reach third-party listeners registered at the GLFW layer |
| OS-level simulation | Java Robot | Any target outside the game's input pipeline | Requires window focus |

---

## 6. F8 Master Control

Pressing F8 executes the **action sequence** (each step toggleable in settings):

1. Script start/stop
2. Attack/destroy mode switch (the `AttackMode` option in `GameOptions`; field name verified at implementation time, mixin accessor as fallback; in toggle mode one left-click latches continuous attack — on stop, one click unlatches and mode reverts to HOLD)
3. **External hotkey trigger: default PgDn** (the user's mouse-lock hotkey, e.g. Lunar built-in), per-key configurable method: `inject` (in-game injection) / `os` (OS-level simulation)
4. HUD toggle

> Environment note: when switching to the plain-Fabric fallback environment, bind SkyHanni's mouse-lock hotkey to the same key (PgDn) so both environments behave identically with zero config changes.

---

## 7. HUD

- Lunar style: corner text, four corner presets + offset, adjustable background/opacity/font scale.
- **Customizable template text**, placeholders: `{state} {script} {step} {col}/{total} {timeLeft} {attackMode}`; default template in Chinese.
- **Silent mode**: no rendering, no notifications of any kind.
- No action-bar messages (to avoid interfering with the UI).

---

## 8. Configuration Storage

```
config/sky_script/
├─ settings.json      # Global: key semantics, HUD template, F8 action toggles, external hotkey config
└─ scripts/*.json     # One file per script
```

---

## 9. In-Game Editor

Three screens (the largest work item; estimated 800–1500 lines; no technical risk):

1. **Script list**: view / create / delete / set active
2. **Step list**: view / add / remove / reorder steps
3. **Step edit**: type selector, coordinate/time input fields, key recorder

Plus the **settings screen** (opened via `/skyscript` or Mod Menu): HUD options, key semantics, trigger keys, master/editor key names, F8 action toggles, external hotkey, feedback toggle, restore defaults.

---

## 10. Environment & Version Strategy

- **Standard client-side Fabric mod**, compatible with any Fabric environment.
- **M0 field verification** (deferred): whether Lunar Client 1.21.11 loads the mod jar.
  - Path A: loads → keep the Lunar environment.
  - Path B (fallback): plain Fabric = Fabric Loader + Fabric API + SkyHanni (Modrinth build) + SkyScript.
- **Version strategy**: single-version on 1.21.11 with version-isolation discipline (version-specific code concentrated in a few files under `input/` and `mixins/`, with a porting checklist); when 26.1/26.2 (year-based versioning) are released, introduce Stonecutter if dual-version maintenance is required.

---

## 11. Milestones

| Phase | Scope | Estimate |
|---|---|---|
| M0 | ~~Pre-verification~~ deferred: merged into M1 field test (Lunar loading + AttackMode field confirmation) | 0 |
| M1 | Skeleton + keybind framework + movement-injection mixin + HUD + time-based steps | 2–3 days |
| M2 | Full engine: position/command/press/loop + all stop conditions + key semantics settings | 2–3 days |
| M3 | Editor GUI (three screens) | 3–5 days |
| M4 | F8 orchestration: AttackMode read/write + left-click injection + external hotkeys + Lunar field tuning | 2–3 days |
| M5 | Version isolation + porting checklist | 1–2 days |

Total ≈ 2–3 weeks; estimated 2500–4000 lines of code.

---

## 12. Risk Register

1. **Lunar 1.21.11 Fabric loading compatibility** (highest priority; M0 field test; fallback ready)
2. **Whether Lunar-internal features respond to in-game event injection** (OS-level simulation as fallback)
3. **Server anti-macro rules**: a client-side mod adds no detection surface, but automation behavior itself is subject to server rules
4. **AttackMode field name changes across versions** (mixin accessor as fallback)

---

## 13. Naming & Publishing

- Display name: SkyScript
- Mod id: `sky_script`
- Check for name conflicts on Modrinth before publishing.

# SkyScript

> A programmable SkyBlock farming automation framework for Minecraft 1.21.11 (client-side Fabric mod).

SkyScript evolved from a simple AutoHotkey script (`AutoKey.ahk`) into a full in-game automation framework: JSON step-based scripts (time / coordinate / manual triggers), a one-key master control (script toggle + attack/destroy mode switch + external hotkey triggers + HUD toggle), an in-game script editor, and a fully customizable Lunar-style HUD.

## Repository Layout

| Path | Description |
|---|---|
| `DESIGN.md` | Design specification (architecture / DSL / state machine / input layers / milestones / risks) |
| `AutoKey.ahk` | Original AHK script (historical reference) |
| `examples/` | Example script JSON files |
| `build/libs/skyscript-0.1.1.jar` | **Release artifact (build output)** |
| `.toolchain/` | Local toolchain (Gradle distribution, push script). Git-ignored; not part of the repository. |

## Installation

1. Requirements: [Fabric Loader](https://fabricmc.net/use/) (0.17+), [Fabric API](https://modrinth.com/mod/fabric-api) (1.21.11 build), Minecraft 1.21.11 (Java 21+).
2. Copy `build/libs/skyscript-0.1.1.jar` into `.minecraft/mods/`.
3. Optional: if your environment supports loading Fabric mods (e.g. Lunar Client with Fabric support), the mod may be placed there as well; otherwise use a plain Fabric client with the Modrinth build of SkyHanni.

## Quick Start

1. In-game, run **`/skyscript`** (opens Settings) or press **H** (opens the script editor). With [Mod Menu](https://modrinth.com/mod/modmenu) installed, the "Settings" entry for SkyScript is also available from the mod list.
2. In the script editor, select "New Script" — a default AHK-style A/D alternation template is generated (120 s per row, 0.5 s pause between rows).
3. Press "Done" to save and return to the game.
4. Press **F8** to start everything at once: script begins, attack/destroy mode switches to *toggle* (one click latches continuous harvesting), and the external hotkey (default `PgDn`, e.g. your mouse-lock binding) is triggered. Chat feedback confirms the action.
5. Press **F8** again to stop: script halts, attack/destroy mode reverts to *hold*, and the external hotkey is triggered once more.

Alternatively, start a script by **clicking A or D** (release-edge trigger). While running, press the other direction key to switch direction, or the current key to stop (configurable).

## Entry Points and Key Bindings

| Entry | Function |
|---|---|
| `/skyscript` | Open the **settings screen** (HUD position/template/scale, key semantics, trigger keys, F8 action toggles, external hotkeys, feedback toggle) |
| `/skyscript editor` | Open the script editor |
| `/skyscript help` | Show command help |
| Mod Menu → SkyScript → Settings | Same as `/skyscript` (requires Mod Menu) |
| H | Open the script editor (renamable in settings) |
| F8 | Master control: script toggle + attack/destroy mode + external hotkeys + HUD toggle (all configurable; the key itself is renamable in settings) |
| A / D | Start the active script when idle (release-edge trigger); switch direction / stop while running (configurable semantics) |

> Key detection is dual-channel (GLFW polling + KeyBinding) and remains responsive under any timing conditions.
> All start/stop/direction-switch actions produce chat feedback (configurable). The HUD line color reflects state: **running = green / frozen = yellow / idle = gray**.

## Script Format (`config/sky_script/scripts/*.json`)

```json
{
  "name": "pumpkin-farm",
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

| Step type | Fields | Description |
|---|---|---|
| `hold` | `keys`, `untilType` | Hold a set of keys until a condition is met; `untilType`: `time` (with `ms`), `position` (with `cond` axis/operator/value), or `manual` |
| `wait` | `ms` | Wait for the given number of milliseconds |
| `press` | `keys`, `mode` | `tap` (instant press-release) or `hold` (hold until condition, same `untilType` semantics) |
| `command` | `value` | Send a chat command, e.g. `/home` (bypasses the chat UI) |
| `loop` | `times`, `body` | Repeat the body `times` times |

- Script-level `loop`: `0` = run indefinitely until stopped manually (default); `N` = stop after N full rounds.
- Key names: `A` `D` `W` `S` `SPACE` `LSHIFT` `F1`–`F24` `PGDN` `PGUP` `ENTER` `ESC`, etc. (the editor supports key recording).
- **Direction alternation**: lateral hold steps (a single A/D key) are mapped positionally — starting with A yields A,D,A,D…, starting with D yields D,A,D,A…, and switching mid-run follows the same rule (matches the original AHK behavior).

## Global Settings (`config/sky_script/settings.json`)

```json
{
  "currentKeySemantics": "ignore",
  "otherKeySemantics": "switch",
  "triggerKeys": ["A", "D"],
  "activeScript": "pumpkin-farm",
  "masterKeyName": "F8",
  "editorKeyName": "H",
  "hud": {
    "enabled": true, "silent": false,
    "template": "SkyScript §7{state} §f{script} §7{step} §f{timeLeft}s §7{attackMode}",
    "pos": "top-left", "x": 4, "y": 4, "background": true, "scale": 1.0
  },
  "master": {
    "toggleScript": true, "toggleAttackMode": true, "toggleHud": true, "feedback": true,
    "externalKeys": [ { "key": "PGDN", "method": "inject" } ]
  }
}
```

HUD placeholders: `{state}` `{script}` `{step}` `{col}` `{total}` `{timeLeft}` `{attackMode}`.
External hotkey `method`: `inject` (in-game event injection, works with MC keybind-based features such as SkyHanni) or `os` (OS-level simulation for targets outside the game's input pipeline, e.g. Lunar-internal hotkeys; requires window focus).

## Behavior Notes

- **Screen freeze**: opening any screen (inventory, chat, crafting, pause) pauses the script timer and movement; closing the screen resumes.
- **Automatic stop**: disconnection, death, or completion of a finite-loop script stops execution automatically.
- **Position triggers**: absolute coordinates (axis + operator + value), evaluated every tick.
- **Movement injection**: directly overrides `Input.playerInput` / `movementVector` via mixin, equivalent to holding keys, without requiring window focus.

## Building

```bash
gradlew build          # artifacts in build/libs/
```

Toolchain: JDK 21+, Gradle 9.5+ (required by Loom 1.17.19), Fabric API 0.141.6+1.21.11, Yarn 1.21.11+build.6.

## Pushing to GitHub (Network-Restricted Environment)

This machine's network filters HTTPS connections to `github.com` (connections are reset), so a standard `git push` cannot connect directly. Two alternative channels are configured; **use the script for routine pushes**:

```powershell
# Option 1 (recommended): GitHub git-data API via api.github.com (reachable)
git add -A && git commit -m "your message"
pwsh -File .toolchain\push-via-api.ps1

# Option 2: SSH over port 443 (~/.ssh/config routes github.com through ssh.github.com:443)
# Requires registering the local SSH public key with the GitHub account:
#   gh auth refresh -h github.com -s admin:public_key && gh ssh-key add ~/.ssh/id_ed25519.pub
git push origin main
```

> Note: `.toolchain\` is git-ignored (contains the local Gradle distribution and the push script) and is not part of the repository. A deploy key is registered for this repository; once an account-level SSH key is available, Option 2 can be used directly.

## Compliance Notice

- Whether automation is permitted is determined by the server's rules (e.g. Hypixel explicitly prohibits macros).
- This mod is client-side only: no server-side installation is required and no game files are modified.

## Release History

- **v0.1.1** — Settings screen (`/skyscript`), Mod Menu integration, dual-channel key detection, chat feedback, state-colored HUD.
- **v0.1.0** — Initial release: script engine, input injection, F8 master control, HUD, in-game editor.

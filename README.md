# SneakyPoses

A Paper 1.21+ plugin for controlling player poses — sit, crawl, and sleep — with smooth NPC-based rendering and full client synchronization.

Made by [Team Sneakymouse](https://rawb.tv).

---

## Features

- **`/sit`** — Mounts the player on an invisible `BlockDisplay` vehicle, locking them into a seated position.
- **Sit on blocks** — Optional: right-click configured block materials or Minecraft block tags (same permission as `/sit`) with a per-entry Y offset. Horizontal anchor is the block center (X/Z +0.5) except **bottom-half stairs**, which snap to the **lower tread** (±0.25 on X and/or Z from center, including corner stair shapes). **Double slabs** never trigger; **sneaking** disables click-to-sit. Right-click again while sitting to stand.
- **`/crawl`** — Forces the player into a crawling pose.
- **`/sleep`** — Spawns a skinned, fake player NPC in a sleeping pose at the player's location. The real player is made invisible. Head rotation tracks where the player looks (clamped to ±45° to prevent neck-breaking).
- **Safe dismount** — When leaving a pose, players are teleported to the nearest non-solid block (prioritizing upward Y) to prevent clipping into the ground.
- **Orphan cleanup** — On startup, any stranded seat entities from previous crashes are automatically removed (identified by the `SneakyPosesSeat` scoreboard tag).
- **Packet-based NPC** — The sleeping NPC is purely packet-side; no real player entity is registered on the server, eliminating all anti-cheat and cleanup risks.

---

## Commands

| Command  | Description                        | Permission              |
|----------|------------------------------------|-------------------------|
| `/sit`   | Toggle sitting pose                | `sneakyposes.sit`       |
| `/sleep` | Toggle sleeping pose               | `sneakyposes.sleep`     |
| `/crawl` | Toggle crawling pose               | `sneakyposes.crawl`     |
| `/pose`  | Base command / help                | `sneakyposes.use`       |

### Pose command arguments (`/sit`, `/crawl`, `/sleep`)

Arguments are fixed-order:

1. **`true` | `false` | `toggle`** — Optional for players. If omitted, the command toggles your pose. If present, it must be exactly one of these three words (no aliases such as `on`, `off`, or `1` / `0`). The console must always include this token.
2. **`<player>`** — Optional for players (defaults to you). Required for the console. Tab completion lists online players only if you have `sneakyposes.others` or you are the console.
3. **`world,x,y,z`** — Optional custom location (four comma-separated parts). Tab completion suggests the placeholder format only under the same rules as player names (`sneakyposes.others` or console).

Targeting another player requires `sneakyposes.others` (or the console). You cannot put a player name or coordinates in the first slot; use the order above.

---

## Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%sneakyposes_sitting%` | Returns `true` if the player is sitting, `false` otherwise. |
| `%sneakyposes_crawling%` | Returns `true` if the player is crawling, `false` otherwise. |
| `%sneakyposes_sleeping%` | Returns `true` if the player is sleeping, `false` otherwise. |
| `%sneakyposes_posing%` | Returns `true` if the player is posing, `false` otherwise. |

---

## Configuration

`plugins/SneakyPoses/config.yml`:

```yaml
sit:
  y-offset: 0.1        # Vertical offset for `/sit` (and default anchor behavior)
  click-blocks:        # Optional: right-click to sit (first match wins)
    - material: OAK_STAIRS
      y-offset: 0.35    # Replaces sit.y-offset for this interaction only
    - tag: minecraft:stairs
      y-offset: 0.3
crawl:
  auto-crawl:
    pitch-tolerance: 30    # Degrees below horizontal required to trigger crawl on double-shift
    cooldown-ticks: 10     # Ticks before another shift can cancel the crawl (prevents accidental triple-shift)
sleep:
  y-offset: 0.1        # Vertical offset of the NPC above the bed location
  npc-name: "[playerName]"  # Display name of the sleeping NPC
```

Each `click-blocks` entry must set exactly one of `material` (Bukkit material name) or `tag` (namespaced block tag, e.g. `minecraft:stairs`), plus `y-offset`. Invalid rows are skipped with a console warning. Changes apply after `/pose reload`.

### Sit on stairs (right-click)

For blocks whose `BlockData` is stairs (`minecraft:stairs` entries included), only **`half: bottom`** stairs are adjusted: the sit anchor moves from the block center to the **lower step** using the stair’s `facing` and `shape` (`straight`, `inner_left`, `inner_right`, `outer_left`, `outer_right`). **Top-half (upside-down) stairs** keep the normal block-center X/Z so you are not shifted onto the wrong geometry.

### NPC Name Placeholders

The `npc-name` field supports:

- `[playerName]` — Replaced with the sleeping player's username.
- Any [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) placeholder (e.g. `%player_displayname%`) if PlaceholderAPI is installed.

> **Note:** Minecraft tab-list names are capped at 16 characters. Names longer than this will be automatically truncated.

---

## Dependencies

| Dependency       | Required | Notes                              |
|------------------|----------|------------------------------------|
| Paper 1.21.4+    | ✅ Yes   | Not compatible with Spigot/Bukkit  |
| PlaceholderAPI   | ❌ No    | Enables dynamic NPC name placeholders |

---

## Installation

1. Drop `SneakyPoses.jar` into your `plugins/` folder.
2. *(Optional)* Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for dynamic NPC names.
3. Start the server. A default `config.yml` will be generated.

---

## Technical Notes

- **Seat entities** are real `BlockDisplay` entities tagged with `SneakyPosesSeat` for reliable server-side passenger locking. They are cleaned up on dismount and on plugin disable.
- **Sleeping NPCs** are constructed using NMS `ServerPlayer` reflection — no real entity is added to the world. Skin data is copied from the player's existing `GameProfile`.
- **Head tracking** is performed via a 1-tick Bukkit scheduler that broadcasts `ClientboundRotateHeadPacket` to nearby players, with the yaw clamped to ±45° relative to the bed's facing direction.

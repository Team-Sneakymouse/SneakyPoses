# SneakyPoses

A Paper 1.21+ plugin for controlling player poses — sit, crawl, and sleep — with smooth NPC-based rendering and full client synchronization.

Made by [Team Sneakymouse](https://rawb.tv).

---

## Features

- **`/sit`** — Mounts the player on an invisible `BlockDisplay` vehicle, locking them into a seated position.
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

---

## Configuration

`plugins/SneakyPoses/config.yml`:

```yaml
sit:
  y-offset: 0.1        # Vertical offset of the seat entity
crawl:
  auto-crawl:
    pitch-tolerance: 30    # Degrees below horizontal required to trigger crawl on double-shift
    cooldown-ticks: 10     # Ticks before another shift can cancel the crawl (prevents accidental triple-shift)
sleep:
  y-offset: 0.1        # Vertical offset of the NPC above the bed location
  npc-name: "[playerName]"  # Display name of the sleeping NPC
```

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

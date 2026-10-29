# Agent instructions (SneakyPoses)

Paper plugin in Team-Sneakymouse / Lords of Minecraft. Follow the shared rules in EmptyPlugin's `AGENTS.md` and the team development policy.

## Repo-specific rules

- Every pose (sit vehicle, crawl, sleep NPC) must be torn down by `PoseListenerCleanup.cleanupPose`/`cleanupAll` on quit, death, world change, teleport and disable. Add new exit paths there.
- SneakyDancing asks about pose state through this plugin by name (see its `WavePreview`). Keep a stable read-only way to tell whether a player is standing.
- Delayed tasks in `util/PacketManager.kt` capture a player; re-check the player is still online and the plugin enabled (as the `isEnabled` check does) before sending packets.
- `build_output.txt` is tracked but should not be: remove it and add it to `.gitignore` instead of committing new build logs.
- Block-sit config and offsets are parsed in `SitClickRules` and `StairSitAnchor`, which have tests; extend them there.

package com.sneakyposes.listeners

import com.sneakyposes.util.CrawlManager
import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import org.bukkit.event.EventPriority
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityToggleSwimEvent
import org.bukkit.entity.Player
import org.bukkit.Bukkit
import java.util.UUID

class PoseListener : Listener {

    // Timestamps of the last sneak press per player (system ms)
    private val lastSneakTime = mutableMapOf<UUID, Long>()
    // Ticks at which the crawl was entered, for cooldown tracking
    private val crawlStartTick = mutableMapOf<UUID, Long>()

    private val DOUBLE_SHIFT_WINDOW_MS = 400L // Must shift twice within this window

    @EventHandler(ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val pose = PoseManager.getPose(player) ?: return

        val configKey = when (pose.type) {
            PoseType.SIT   -> "sit.end-on-teleport"
            PoseType.CRAWL -> "crawl.end-on-teleport"
            PoseType.SLEEP -> "sleep.end-on-teleport"
        }

        if (com.sneakyposes.SneakyPoses.instance.config.getBoolean(configKey, true)) {
            PoseListenerCleanup.cleanupPose(player, relocate = false)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val pose = PoseManager.getPose(player) ?: return

        val configKey = when (pose.type) {
            PoseType.SIT   -> "sit.end-on-damage"
            PoseType.CRAWL -> "crawl.end-on-damage"
            PoseType.SLEEP -> "sleep.end-on-damage"
        }

        if (com.sneakyposes.SneakyPoses.instance.config.getBoolean(configKey, true)) {
            PoseListenerCleanup.cleanupPose(player)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onToggleSwim(event: EntityToggleSwimEvent) {
        val player = event.entity as? Player ?: return
        if (PoseManager.getPose(player)?.type == PoseType.CRAWL) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player

        // Only act on the press (isSneaking = true), not the release
        if (!event.isSneaking) return

        val plugin = com.sneakyposes.SneakyPoses.instance
        val config = plugin.config
        val pitchTolerance = config.getDouble("crawl.auto-crawl.pitch-tolerance", 30.0).toFloat()
        val cooldownTicks = config.getLong("crawl.auto-crawl.cooldown-ticks", 40L)

        val pose = PoseManager.getPose(player)

        // ── Already in crawl ────────────────────────────────────────────
        if (pose != null && pose.type == PoseType.CRAWL) {
            val enteredTick = crawlStartTick[player.uniqueId] ?: 0L
            val currentTick = Bukkit.getCurrentTick().toLong()
            if (currentTick - enteredTick < cooldownTicks) {
                // Still within cooldown — eat the shift silently
                return
            }
            cleanupPose(player)
            lastSneakTime.remove(player.uniqueId)
            crawlStartTick.remove(player.uniqueId)
            return
        }

        // ── Already in another pose (sleep / sit) ───────────────────────
        if (pose != null) {
            cleanupPose(player)
            lastSneakTime.remove(player.uniqueId)
            return
        }

        // ── Not posing — check for double-shift + looking down ───────────
        val now = System.currentTimeMillis()
        val last = lastSneakTime[player.uniqueId]

        if (last != null && now - last <= DOUBLE_SHIFT_WINDOW_MS) {
            // This is the second shift — check pitch
            val pitch = player.location.pitch // positive = looking down in Minecraft
            if (pitch >= pitchTolerance) {
                // Trigger crawl
                startCrawl(player)
                lastSneakTime.remove(player.uniqueId)
                return
            }
        }

        lastSneakTime[player.uniqueId] = now
    }

    private fun startCrawl(player: Player) {
        CrawlManager.beginCrawl(player, player.location)
        crawlStartTick[player.uniqueId] = Bukkit.getCurrentTick().toLong()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (event.isAsynchronous) return
        val player = event.player
        if (PoseManager.getPose(player)?.type != PoseType.CRAWL) return

        val from = event.from
        val to = event.to ?: return
        if (from.x != to.x || from.y != to.y || from.z != to.z) {
            CrawlManager.tick(player, to)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        cleanupPose(event.player)
        lastSneakTime.remove(uuid)
        crawlStartTick.remove(uuid)
        
        // Remove viewer from all active tracking sets
        PoseManager.getAllActivePoses().values.forEach { it.viewerUuids.remove(uuid) }
    }

    private fun findSafeLocation(start: org.bukkit.Location) = PoseListenerCleanup.findSafeLocation(start)

    private fun cleanupPose(player: Player) = PoseListenerCleanup.cleanupPose(player)
}

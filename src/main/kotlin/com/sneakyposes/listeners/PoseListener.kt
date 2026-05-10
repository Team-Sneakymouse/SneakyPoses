package com.sneakyposes.listeners

import com.sneakyposes.util.PacketManager
import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import com.sneakyposes.util.PoseData
import org.bukkit.Material
import org.bukkit.event.EventPriority
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.entity.EntityDamageEvent
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
        val location = player.location
        val barrierLoc = location.clone().add(0.0, 1.5, 0.0).block.location
        
        val blocks = if (barrierLoc.block.type.isAir) {
            barrierLoc.block.type = Material.BARRIER
            setOf(barrierLoc)
        } else {
            emptySet()
        }

        PoseManager.setPose(player, PoseData(
            type = PoseType.CRAWL,
            location = location,
            blocks = blocks
        ))
        crawlStartTick[player.uniqueId] = Bukkit.getCurrentTick().toLong()
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val pose = PoseManager.getPose(player) ?: return

        if (pose.type == PoseType.CRAWL) {
            val fromBlock = event.from.block
            val toBlock = event.to.block
            
            if (fromBlock != toBlock) {
                // Remove old barrier (REAL)
                pose.blocks.forEach { 
                    if (it.block.type == Material.BARRIER) {
                        it.block.type = Material.AIR
                    }
                }
                
                // Place new barrier above head (REAL)
                val newBarrierLoc = event.to.clone().add(0.0, 1.5, 0.0).block.location
                val newBlocks = if (newBarrierLoc.block.type.isAir) {
                    newBarrierLoc.block.type = Material.BARRIER
                    setOf(newBarrierLoc)
                } else {
                    emptySet()
                }
                
                // Save data
                val newData = pose.copy(blocks = newBlocks)
                PoseManager.setPose(player, newData)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cleanupPose(event.player)
        lastSneakTime.remove(event.player.uniqueId)
        crawlStartTick.remove(event.player.uniqueId)
    }

    private fun findSafeLocation(start: org.bukkit.Location) = PoseListenerCleanup.findSafeLocation(start)

    private fun cleanupPose(player: Player) = PoseListenerCleanup.cleanupPose(player)
}

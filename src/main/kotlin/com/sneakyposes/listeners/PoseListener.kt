package com.sneakyposes.listeners

import com.sneakyposes.util.PacketManager
import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import org.bukkit.Material
import org.bukkit.event.EventPriority
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.entity.Player
import org.bukkit.Bukkit

class PoseListener : Listener {


    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (event.isSneaking && PoseManager.isPosing(player)) {
            cleanupPose(player)
        }
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val pose = PoseManager.getPose(player) ?: return

        if (pose.type == PoseType.CRAWL) {
            val fromBlock = event.from.block
            val toBlock = event.to.block
            
            if (fromBlock != toBlock) {
                // Remove old barrier (fake)
                pose.blocks.forEach { 
                    PacketManager.clearBlockChange(player, it)
                }
                
                // Place new barrier above head (fake)
                val newBarrierLoc = event.to.clone().add(0.0, 1.8, 0.0) // Above head
                PacketManager.sendBlockChange(player, newBarrierLoc, Material.BARRIER)
                
                // Save data
                val newData = pose.copy(blocks = setOf(newBarrierLoc))
                PoseManager.setPose(player, newData)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cleanupPose(event.player)
    }

    private fun cleanupPose(player: Player) {
        val pose = PoseManager.removePose(player) ?: return
        
        // Eject player before removing entities
        player.leaveVehicle()

        // Remove entities
        pose.entityUuids.forEach { uuid ->
            Bukkit.getEntity(uuid)?.remove()
        }
        
        // Clean up blocks (for player)
        pose.blocks.forEach { loc ->
            PacketManager.clearBlockChange(player, loc)
        }
        
        // If it was sleep, remove NPC and clear bed for all viewers
        if (pose.type == PoseType.SLEEP && pose.npcId != null && pose.npcUuid != null) {
            val bedLoc = pose.blocks.firstOrNull() // The bed we tracked
            PacketManager.removeSleepNPC(player, pose.npcId, pose.npcUuid, bedLoc)
        }
    }
}

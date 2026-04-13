package com.sneakyposes.listeners

import com.sneakyposes.util.PacketManager
import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Shared cleanup logic accessible from both PoseListener and CommandBasePose.
 */
object PoseListenerCleanup {

    fun cleanupPose(player: Player) {
        val pose = PoseManager.removePose(player) ?: return

        player.isInvisible = false
        player.leaveVehicle()

        val safeLoc = findSafeLocation(pose.location)
        Bukkit.getScheduler().runTask(com.sneakyposes.SneakyPoses.instance, Runnable {
            player.teleport(safeLoc)
        })

        pose.entityUuids.forEach { uuid ->
            Bukkit.getEntity(uuid)?.remove()
        }

        pose.blocks.forEach { loc ->
            PacketManager.clearBlockChange(player, loc)
        }

        if (pose.type == PoseType.SLEEP && pose.npcId != null && pose.npcUuid != null) {
            val bedLoc = pose.blocks.firstOrNull()
            PacketManager.removeSleepNPC(player, pose.npcId, pose.npcUuid, bedLoc)
        }
    }

    fun findSafeLocation(start: org.bukkit.Location): org.bukkit.Location {
        val world = start.world
        val blockLoc = start.block.location
        val yOffsets = listOf(0, 1, 2, 3, -1, -2)

        for (radius in 0..2) {
            for (yOff in yOffsets) {
                for (x in -radius..radius) {
                    for (z in -radius..radius) {
                        if (radius == 0 || Math.abs(x) == radius || Math.abs(z) == radius) {
                            val b1 = blockLoc.clone().add(x.toDouble(), yOff.toDouble(), z.toDouble()).block
                            val b2 = b1.getRelative(org.bukkit.block.BlockFace.UP)
                            if (!b1.type.isSolid && !b2.type.isSolid) {
                                return org.bukkit.Location(world, b1.x + 0.5, b1.y.toDouble(), b1.z + 0.5, start.yaw, start.pitch)
                            }
                        }
                    }
                }
            }
        }
        return start.clone().add(0.0, 1.0, 0.0)
    }
}

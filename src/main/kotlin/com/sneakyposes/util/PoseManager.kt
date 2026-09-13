package com.sneakyposes.util

import com.sneakyposes.listeners.PoseListenerCleanup
import org.bukkit.entity.Player
import org.bukkit.Location
import java.util.*

enum class PoseType {
    SIT, CRAWL, SLEEP
}

data class PoseData(
    val type: PoseType,
    val location: Location,
    val entityUuids: Set<UUID> = emptySet(),
    val blocks: Set<Location> = emptySet(),
    val npcId: Int? = null,
    val npcUuid: UUID? = null,
    val npcEntity: Any? = null,
    val viewerUuids: MutableSet<UUID> = mutableSetOf(),
    /** Yaw to restore when leaving sleep (NPC yaw is rotated 180° from this). */
    val wakeYaw: Float? = null
)

object PoseManager {
    private val activePoses = mutableMapOf<UUID, PoseData>()

    fun setPose(player: Player, poseData: PoseData) {
        if (isPosing(player)) {
            PoseListenerCleanup.cleanupPose(player, relocate = false)
        }
        activePoses[player.uniqueId] = poseData
    }

    fun getPose(player: Player): PoseData? {
        return activePoses[player.uniqueId]
    }

    fun removePose(player: Player): PoseData? {
        return activePoses.remove(player.uniqueId)
    }

    fun isPosing(player: Player): Boolean {
        return activePoses.containsKey(player.uniqueId)
    }
    
    fun getAllPosingPlayers(): Collection<PoseData> = activePoses.values

    fun getAllActivePoses(): Map<UUID, PoseData> = activePoses.toMap()
}

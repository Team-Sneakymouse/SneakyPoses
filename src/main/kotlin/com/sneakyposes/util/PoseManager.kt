package com.sneakyposes.util

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
    val npcUuid: UUID? = null
)

object PoseManager {
    private val activePoses = mutableMapOf<UUID, PoseData>()

    fun setPose(player: Player, poseData: PoseData) {
        // Cleanup existing pose if any
        removePose(player)
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
}

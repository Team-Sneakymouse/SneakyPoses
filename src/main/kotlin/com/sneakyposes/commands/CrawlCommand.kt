package com.sneakyposes.commands

import com.sneakyposes.SneakyPoses
import com.sneakyposes.util.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

class CrawlCommand : CommandBasePose("crawl") {

    init {
        aliases = listOf("bellyflop")
    }

    override val poseType = PoseType.CRAWL

    override fun applyPose(sender: CommandSender, target: Player, location: Location) {
        val barrierLoc = location.clone().add(0.0, 1.5, 0.0).block.location
        
        val entities = mutableSetOf<UUID>()
        val blocks = if (barrierLoc.block.type.isAir) {
            barrierLoc.block.type = Material.BARRIER
            
            // Spawn marker for crash recovery
            val marker = barrierLoc.world.spawn(barrierLoc.clone().add(0.5, 0.0, 0.5), org.bukkit.entity.Marker::class.java) {
                it.addScoreboardTag("SneakyPosesBarrierMarker")
            }
            entities.add(marker.uniqueId)
            
            setOf(barrierLoc)
        } else {
            emptySet()
        }

        PoseManager.setPose(target, PoseData(
            type = PoseType.CRAWL,
            location = location,
            blocks = blocks,
            entityUuids = entities
        ))

        if (sender != target) {
            sender.sendMessage("Crawling ${target.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        }
    }
}

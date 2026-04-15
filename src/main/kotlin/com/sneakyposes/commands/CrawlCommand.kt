package com.sneakyposes.commands

import com.sneakyposes.util.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CrawlCommand : CommandBasePose("crawl") {

    init {
        aliases = listOf("bellyflop")
    }

    override val poseType = PoseType.CRAWL

    override fun applyPose(sender: CommandSender, target: Player, location: Location) {
        val headLoc = location.clone().add(0.0, 1.8, 0.0)
        PacketManager.sendBlockChange(target, headLoc, Material.BARRIER)

        PoseManager.setPose(target, PoseData(
            type = PoseType.CRAWL,
            location = location,
            blocks = setOf(headLoc)
        ))

        if (sender != target) {
            sender.sendMessage("Crawling ${target.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        }
    }
}

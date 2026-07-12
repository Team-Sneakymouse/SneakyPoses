package com.sneakyposes.commands

import com.sneakyposes.util.CrawlManager
import com.sneakyposes.util.PoseType
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CrawlCommand : CommandBasePose("crawl") {

    init {
        aliases = listOf("bellyflop")
    }

    override val poseType = PoseType.CRAWL

    override fun applyPose(sender: CommandSender, target: Player, location: Location) {
        CrawlManager.beginCrawl(target, location)

        if (sender != target) {
            sender.sendMessage("Crawling ${target.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        }
    }
}

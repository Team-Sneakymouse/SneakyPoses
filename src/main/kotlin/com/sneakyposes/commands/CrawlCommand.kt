package com.sneakyposes.commands

import com.sneakyposes.util.*
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CrawlCommand : CommandBase("crawl") {

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission(permission!!)) {
            sender.sendMessage("No permission.")
            return true
        }

        val (target, location) = CommandUtility.getTargetAndLocation(sender, args)

        if (target == null || location == null) {
            sender.sendMessage("Usage: /crawl [player] [world,x,y,z]")
            return true
        }

        // Toggling crawling
        if (PoseManager.isPosing(target) && PoseManager.getPose(target)?.type == PoseType.CRAWL) {
            sender.sendMessage("Stopped crawling for ${target.name}")
            // Trigger cleanup by removing pose
            // PoseListener would normally handle this but we can call it directly
            return true
        }

        // Place initial barrier above head (fake)
        val headLoc = location.clone().add(0.0, 1.8, 0.0)
        PacketManager.sendBlockChange(target, headLoc, Material.BARRIER)

        // Save pose data
        PoseManager.setPose(target, PoseData(
            type = PoseType.CRAWL,
            location = location,
            blocks = setOf(headLoc)
        ))

        if (sender != target) {
            sender.sendMessage("Started crawling for ${target.name}")
        }
        return true
    }
}

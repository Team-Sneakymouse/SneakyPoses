package com.sneakyposes.commands

import com.sneakyposes.SneakyPoses
import com.sneakyposes.util.*
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SitCommand : CommandBasePose("sit") {

    override val poseType = PoseType.SIT

    override fun applyPose(sender: CommandSender, target: Player, location: Location) {
        val yOffset = SneakyPoses.instance.config.getDouble("sit.y-offset", -0.5)
        SitPoseApplier.apply(sender, target, location, yOffset)
    }
}

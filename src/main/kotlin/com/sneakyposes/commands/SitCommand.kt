package com.sneakyposes.commands

import com.sneakyposes.SneakyPoses
import com.sneakyposes.util.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SitCommand : CommandBase("sit") {

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission(permission!!)) {
            sender.sendMessage("No permission.")
            return true
        }

        val (target, location) = CommandUtility.getTargetAndLocation(sender, args)

        if (target == null || location == null) {
            sender.sendMessage("Usage: /sit [player] [world,x,y,z]")
            return true
        }

        val yOffset = SneakyPoses.instance.config.getDouble("sit.y-offset", -0.5)
        val sitLocation = location.clone().add(0.0, yOffset, 0.0)

        // Spawn actual invisible vehicle (ArmorStand is more reliable for invisibility)
        val vehicle = PacketManager.spawnSitVehicle(sitLocation, target)
        vehicle.addPassenger(target)

        // Save pose data
        PoseManager.setPose(target, PoseData(
            type = PoseType.SIT,
            location = location,
            entityUuids = setOf(vehicle.uniqueId)
        ))

        if (sender != target) {
            sender.sendMessage("Sitting ${target.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        }
        return true
    }
}

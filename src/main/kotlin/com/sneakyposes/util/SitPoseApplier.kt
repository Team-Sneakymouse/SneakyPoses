package com.sneakyposes.util

import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object SitPoseApplier {

    fun apply(sender: CommandSender, target: Player, location: Location, yOffset: Double) {
        val sitLocation = location.clone().add(0.0, yOffset, 0.0)

        val vehicle = PacketManager.spawnSitVehicle(sitLocation, target)
        vehicle.addPassenger(target)

        PoseManager.setPose(
            target,
            PoseData(
                type = PoseType.SIT,
                location = location,
                entityUuids = setOf(vehicle.uniqueId)
            )
        )

        if (sender != target) {
            sender.sendMessage("Sitting ${target.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        }
    }
}

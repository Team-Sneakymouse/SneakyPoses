package com.sneakyposes.commands

import com.sneakyposes.SneakyPoses
import com.sneakyposes.util.*
import org.bukkit.Material
import org.bukkit.block.data.type.Bed
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.block.BlockFace

class SleepCommand : CommandBase("sleep") {

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission(permission!!)) {
            sender.sendMessage("No permission.")
            return true
        }

        val (target, location) = CommandUtility.getTargetAndLocation(sender, args)

        if (target == null || location == null) {
            sender.sendMessage("Usage: /sleep [player] [world,x,y,z]")
            return true
        }

        val yOffset = SneakyPoses.instance.config.getDouble("sleep.y-offset", -0.1)
        
        // Find the ground block (first solid block underneath)
        var ground = location.block.getRelative(BlockFace.DOWN)
        while (ground.y > ground.world.minHeight && !ground.type.isSolid) {
            ground = ground.getRelative(BlockFace.DOWN)
        }
        
        // GSit Strategy: Metadata points to fake bed at minHeight
        val bedLoc = location.clone()
        bedLoc.y = location.world.minHeight.toDouble()
        
        val face = when {
            location.yaw in -45.0..45.0 -> BlockFace.SOUTH
            location.yaw in 45.0..135.0 -> BlockFace.WEST
            location.yaw in 135.0..180.0 || location.yaw in -180.0..-135.0 -> BlockFace.NORTH
            else -> BlockFace.EAST
        }

        val bedData = Material.RED_BED.createBlockData() as Bed
        bedData.facing = face
        bedData.part = Bed.Part.HEAD

        // Teleport player to ground + yOffset
        val sleepTargetLoc = location.clone()
        sleepTargetLoc.y = ground.y + 1.0 + yOffset
        target.teleport(sleepTargetLoc)

        // Send bed block (fake) at world bottom
        PacketManager.sendBlockChange(target, bedLoc, bedData)

        // Create ghost NPC
        val npcData = PacketManager.spawnSleepNPC(target, sleepTargetLoc) ?: return true
        
        // Use a seat for stability (no shake)
        val vehicle = PacketManager.spawnSitVehicle(location, target)
        vehicle.addPassenger(target)

        // Record pose
        PoseManager.setPose(target, PoseData(
            type = PoseType.SLEEP,
            location = location,
            entityUuids = setOf(vehicle.uniqueId), // Track the seat
            blocks = setOf(bedLoc), // Track the fake bed
            npcId = npcData.first,
            npcUuid = npcData.second
        ))

        if (sender != target) {
            sender.sendMessage("Sleeping ${target.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        }
        return true
    }
}

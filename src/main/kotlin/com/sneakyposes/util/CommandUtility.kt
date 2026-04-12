package com.sneakyposes.util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.command.CommandSender

object CommandUtility {

    /**
     * Parses a target player from a string.
     */
    fun parsePlayer(name: String): Player? {
        return Bukkit.getPlayer(name)
    }

    /**
     * Parses a location from a string in the format "world,x,y,z".
     */
    fun parseLocation(input: String): Location? {
        val parts = input.split(",")
        if (parts.size != 4) return null
        
        val world = Bukkit.getWorld(parts[0]) ?: return null
        val x = parts[1].toDoubleOrNull() ?: return null
        val y = parts[2].toDoubleOrNull() ?: return null
        val z = parts[3].toDoubleOrNull() ?: return null
        
        return Location(world, x, y, z)
    }

    /**
     * Extracts player and location from command arguments.
     * Arguments: [target] [location]
     */
    fun getTargetAndLocation(sender: CommandSender, args: Array<out String>): Pair<Player?, Location?> {
        var target: Player? = if (sender is Player) sender else null
        var location: Location? = if (sender is Player) sender.location else null

        if (args.isNotEmpty()) {
            val potentialPlayer = parsePlayer(args[0])
            if (potentialPlayer != null) {
                target = potentialPlayer
                if (args.size > 1) {
                    val potentialLocation = parseLocation(args[1])
                    if (potentialLocation != null) {
                        location = potentialLocation
                    }
                }
            } else {
                // First arg might be location if sender is player
                val potentialLocation = parseLocation(args[0])
                if (potentialLocation != null) {
                    location = potentialLocation
                }
            }
        }

        return Pair(target, location)
    }
}

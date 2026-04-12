package com.sneakyposes.commands

import com.sneakyposes.SneakyPoses
import org.bukkit.command.CommandSender

class PoseCommand : CommandBase("pose") {

    init {
        permission = "sneakyposes.reload"
        description = "Reloads the SneakyPoses configuration."
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission(permission!!)) {
            sender.sendMessage("No permission.")
            return true
        }

        if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
            SneakyPoses.instance.reloadConfig()
            sender.sendMessage("§a[SneakyPoses] Configuration reloaded.")
            return true
        }

        sender.sendMessage("§cUsage: /pose reload")
        return true
    }
}

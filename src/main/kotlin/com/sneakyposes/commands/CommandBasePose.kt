package com.sneakyposes.commands

import com.sneakyposes.SneakyPoses
import com.sneakyposes.util.CommandUtility
import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Shared base for all pose commands (sit, sleep, crawl).
 *
 * Argument order: /cmd [true|false|toggle] [playerName] [world,x,y,z]
 *
 * Rules:
 *  - Slot 0: only `true`, `false`, or `toggle` (omit from a player to mean toggle)
 *  - Slot 1: player name when present; slot 2: `world,x,y,z` when present
 *  - Targeting another player requires sneakyposes.others (or console)
 *  - Console must supply at least [true|false|toggle] and [playerName]
 */
abstract class CommandBasePose(name: String) : CommandBase(name) {

    companion object {
        const val OTHERS_PERMISSION = "${SneakyPoses.IDENTIFIER}.others"

        private val STATE_VALUES = setOf("true", "false", "toggle")
    }

    /**
     * Apply the pose to the resolved target at the resolved location.
     * Called only when we have confirmed we want to START the pose.
     */
    abstract fun applyPose(sender: CommandSender, target: Player, location: Location)

    /**
     * The PoseType this command manages — used for toggle detection.
     */
    abstract val poseType: PoseType

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        // ── Base permission ──────────────────────────────────────────────
        if (!sender.hasPermission(permission!!)) {
            sender.sendMessage("No permission.")
            return true
        }

        // ── Parse args into (toggle, target, location) ───────────────────
        val senderIsPlayer = sender is Player
        val hasOthers = sender.hasPermission(OTHERS_PERMISSION)

        var wantActive: Boolean? = null  // null = toggle
        var argOffset = 0

        if (args.isNotEmpty()) {
            val first = args[0].lowercase()
            if (first !in STATE_VALUES) {
                sender.sendMessage("Usage: /$name [true|false|toggle] [<player>] [world,x,y,z]")
                return true
            }
            wantActive = when (first) {
                "true"   -> true
                "false"  -> false
                else     -> null // "toggle"
            }
            argOffset = 1
        }

        val remainingArgs = args.drop(argOffset).toTypedArray()

        val target: Player
        val location: Location

        when {
            remainingArgs.isEmpty() -> {
                if (!senderIsPlayer) {
                    sender.sendMessage("Console usage: /$name [true|false|toggle] <player> [world,x,y,z]")
                    return true
                }
                target = sender as Player
                location = target.location
            }
            remainingArgs.size == 1 -> {
                val resolved = CommandUtility.parsePlayer(remainingArgs[0])
                    ?: run {
                        sender.sendMessage("Unknown player: '${remainingArgs[0]}'")
                        return true
                    }
                val isSelf = senderIsPlayer && (sender as Player).uniqueId == resolved.uniqueId
                if (!isSelf && !hasOthers) {
                    sender.sendMessage("You don't have permission to pose other players.")
                    return true
                }
                target = resolved
                location = target.location
            }
            remainingArgs.size == 2 -> {
                val resolved = CommandUtility.parsePlayer(remainingArgs[0])
                    ?: run {
                        sender.sendMessage("Unknown player: '${remainingArgs[0]}'")
                        return true
                    }
                val isSelf = senderIsPlayer && (sender as Player).uniqueId == resolved.uniqueId
                if (!isSelf && !hasOthers) {
                    sender.sendMessage("You don't have permission to pose other players.")
                    return true
                }
                target = resolved
                location = CommandUtility.parseLocation(remainingArgs[1])
                    ?: run {
                        sender.sendMessage("Invalid location format. Use: world,x,y,z")
                        return true
                    }
            }
            else -> {
                sender.sendMessage("Too many arguments. Use: /$name [true|false|toggle] [<player>] [world,x,y,z]")
                return true
            }
        }

        // ── Resolve toggle intent ─────────────────────────────────────────
        val currentlyActive = PoseManager.getPose(target)?.type == poseType

        val shouldActivate = when (wantActive) {
            true  -> true
            false -> false
            null  -> !currentlyActive  // toggle
        }

        // ── Apply ─────────────────────────────────────────────────────────
        if (!shouldActivate) {
            // Stop the pose — PoseListener cleanup path via sneaking normally handles
            // this, but we also support explicit /cmd false
            if (currentlyActive) {
                com.sneakyposes.listeners.PoseListenerCleanup.cleanupPose(target)
                if (sender != target) sender.sendMessage("Stopped ${name}ing for ${target.name}.")
            }
            return true
        }

        if (currentlyActive) {
            // Already in this pose - stop it first (toggle off if same type)
            if (wantActive == null) {
                com.sneakyposes.listeners.PoseListenerCleanup.cleanupPose(target)
                if (sender != target) sender.sendMessage("Stopped ${name}ing for ${target.name}.")
                return true
            }
        }

        applyPose(sender, target, location)
        return true
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
        val hasOthers = sender.hasPermission(OTHERS_PERMISSION)
        val senderIsPlayer = sender is Player
        val suggestPlayers = hasOthers || !senderIsPlayer
        val suggestLocation = hasOthers || !senderIsPlayer

        val currentIndex = args.size - 1
        val current = args.lastOrNull() ?: ""

        return when (currentIndex) {
            0 -> listOf("true", "false", "toggle")
                .filter { it.startsWith(current, ignoreCase = true) }
            1 -> {
                if (!suggestPlayers) return emptyList()
                org.bukkit.Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.startsWith(current, ignoreCase = true) }
            }
            2 -> {
                if (!suggestLocation) return emptyList()
                if (current.isEmpty()) listOf("<world,x,y,z>") else emptyList()
            }
            else -> emptyList()
        }
    }
}

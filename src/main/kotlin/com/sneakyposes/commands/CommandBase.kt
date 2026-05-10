package com.sneakyposes.commands

import com.sneakyposes.SneakyPoses
import org.bukkit.command.Command

/**
 * Base class for all plugin commands.
 * Provides common setup and permission handling.
 *
 * @property name The name of the command
 */
abstract class CommandBase(name: String) : Command(name) {

    init {
        // Permission is handled in execute() to avoid Brigadier sync issues
    }

}
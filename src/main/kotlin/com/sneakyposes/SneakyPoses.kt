package com.sneakyposes

import com.sneakyposes.commands.SitCommand
import com.sneakyposes.commands.CrawlCommand
import com.sneakyposes.commands.SleepCommand
import com.sneakyposes.listeners.PoseListener
import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import org.bukkit.plugin.java.JavaPlugin

class SneakyPoses : JavaPlugin() {

	companion object {
		const val IDENTIFIER = "sneakyposes"
		lateinit var instance: SneakyPoses

		public fun log(message: String) {
			instance.logger.info(message)
		}
	}

	/**
     * Initializes the plugin instance during server load.
     */
    override fun onLoad() {
        instance = this
    }
    
    override fun onEnable() {
        instance = this
        saveDefaultConfig()

        // Register commands via CommandMap since they are Command objects, not Executors
        val commandMap = server.commandMap
        commandMap.register("sneakyposes", SitCommand())
        commandMap.register("sneakyposes", CrawlCommand())
        commandMap.register("sneakyposes", SleepCommand())

        // Register listener
        server.pluginManager.registerEvents(PoseListener(), this)

        // Pose sync task
        server.scheduler.runTaskTimer(this, Runnable {
            for (player in server.onlinePlayers) {
                val pose = PoseManager.getPose(player) ?: continue
                when (pose.type) {
                    PoseType.SIT -> {
                        player.teleport(pose.location.clone().apply { yaw = player.location.yaw })
                    }
                    else -> {}
                }
            }
        }, 0L, 1L)

        logger.info("SneakyPoses plugin has been enabled!")
    }
    
    override fun onDisable() {
        logger.info("SneakyPoses plugin has been disabled!")
    }
    
}

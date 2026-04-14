package com.sneakyposes

import com.sneakyposes.commands.SitCommand
import com.sneakyposes.commands.CrawlCommand
import com.sneakyposes.commands.PoseCommand
import com.sneakyposes.commands.SleepCommand
import com.sneakyposes.listeners.PoseListener
import com.sneakyposes.listeners.SitBlockClickListener
import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import com.sneakyposes.util.SitClickRules
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

        SitClickRules.reload(config)

        // Register commands via CommandMap since they are Command objects, not Executors
        val commandMap = server.commandMap
        commandMap.register("sneakyposes", SitCommand())
        commandMap.register("sneakyposes", CrawlCommand())
        commandMap.register("sneakyposes", SleepCommand())
        commandMap.register("sneakyposes", PoseCommand())

        // Register listener
        server.pluginManager.registerEvents(PoseListener(), this)
        server.pluginManager.registerEvents(SitBlockClickListener(), this)

        // Clean up stranded seats from crashes or improper unloads
        for (world in server.worlds) {
            for (entity in world.entities) {
                if (entity.scoreboardTags.contains("SneakyPosesSeat")) {
                    entity.remove()
                }
            }
        }
        
        // Pose Sync Tasks
        server.scheduler.runTaskTimer(this, Runnable {
            for (player in server.onlinePlayers) {
                val pose = com.sneakyposes.util.PoseManager.getPose(player) ?: continue
                if (pose.type == com.sneakyposes.util.PoseType.SLEEP && pose.npcEntity != null) {
                    com.sneakyposes.util.PacketManager.updateNPCHeadRotation(player, pose.npcEntity, pose.location.yaw)
                }
            }
        }, 0L, 1L)



        // Register PlaceholderAPI expansion if available
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            SneakyPosesExpansion().register()
            logger.info("PlaceholderAPI found — placeholders registered.")
        }

        logger.info("SneakyPoses plugin has been enabled!")
    }
    
    override fun onDisable() {
        logger.info("SneakyPoses plugin has been disabled!")
    }
    
}

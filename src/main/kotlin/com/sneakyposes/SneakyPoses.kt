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
import org.bukkit.entity.Player

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
        val knownCommands = commandMap.knownCommands
        
        // Remove conflicting commands to ensure ours take priority (e.g. over CMI)
        knownCommands.remove("sleep")
        knownCommands.remove("lay")
        knownCommands.remove("sit")
        knownCommands.remove("crawl")
        
        commandMap.register("sneakyposes", SitCommand())
        commandMap.register("sneakyposes", CrawlCommand())
        commandMap.register("sneakyposes", SleepCommand())
        commandMap.register("sneakyposes", PoseCommand())

        // Register listeners
        server.pluginManager.registerEvents(PoseListener(), this)
        server.pluginManager.registerEvents(SitBlockClickListener(), this)

        // Background task for synchronization (Visibility & Head Rotation)
        server.scheduler.runTaskTimer(this, Runnable {
            PoseManager.getAllActivePoses().forEach { (uuid, pose) ->
                val player = server.getPlayer(uuid) ?: return@forEach
                if (pose.type != PoseType.SLEEP || pose.npcEntity == null) return@forEach

                // 1. Sync Visibility for late-joiners
                val playersInRange = player.world.getNearbyEntities(player.location, 48.0, 48.0, 48.0)
                    .filterIsInstance<Player>()
                
                playersInRange.forEach { viewer ->
                    if (!pose.viewerUuids.contains(viewer.uniqueId)) {
                        com.sneakyposes.util.PacketManager.sendNPCPacketsToPlayer(viewer, player, pose.npcEntity, pose.location)
                        pose.viewerUuids.add(viewer.uniqueId)
                    }
                }

                // 2. Periodic Head Rotation Sync
                com.sneakyposes.util.PacketManager.updateNPCHeadRotation(player, pose.npcEntity, (pose.location.yaw + 180f) % 360f)
            }
        }, 20L, 20L)

        // Clean up stranded seats and barriers from crashes or improper unloads
        for (world in server.worlds) {
            for (entity in world.entities) {
                if (entity.scoreboardTags.contains("SneakyPosesSeat")) {
                    entity.remove()
                }
                if (entity.scoreboardTags.contains("SneakyPosesBarrierMarker")) {
                    val loc = entity.location.block.location
                    if (loc.block.type == org.bukkit.Material.BARRIER) {
                        loc.block.type = org.bukkit.Material.AIR
                    }
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
        // Clean up all active poses (blocks, entities, NPCs) globally
        com.sneakyposes.listeners.PoseListenerCleanup.cleanupAll()
        logger.info("SneakyPoses plugin has been disabled!")
    }
    
}

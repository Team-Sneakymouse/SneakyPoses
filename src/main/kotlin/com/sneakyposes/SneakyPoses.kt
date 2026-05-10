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

        var syncCounter = 0
        // Background task for synchronization (Visibility @ 20t, Head Rotation @ 1t)
        server.scheduler.runTaskTimer(this, Runnable {
            syncCounter++
            val checkVisibility = syncCounter % 20 == 0

            PoseManager.getAllActivePoses().forEach { (uuid, pose) ->
                val player = server.getPlayer(uuid) ?: return@forEach
                if (pose.type != PoseType.SLEEP || pose.npcEntity == null) return@forEach

                // 1. Sync Visibility for late-joiners (Every 20 ticks)
                if (checkVisibility) {
                    val playersInRange = player.world.getNearbyEntities(player.location, 48.0, 48.0, 48.0)
                        .filterIsInstance<Player>()
                    
                    playersInRange.forEach { viewer ->
                        if (!pose.viewerUuids.contains(viewer.uniqueId)) {
                            com.sneakyposes.util.PacketManager.sendNPCPacketsToPlayer(viewer, player, pose.npcEntity, pose.location)
                            pose.viewerUuids.add(viewer.uniqueId)
                        }
                    }
                }

                // 2. Continuous Head Rotation Sync (Every tick)
                com.sneakyposes.util.PacketManager.updateNPCHeadRotation(player, pose.npcEntity, pose.location.yaw.toFloat())
            }
        }, 1L, 1L)

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

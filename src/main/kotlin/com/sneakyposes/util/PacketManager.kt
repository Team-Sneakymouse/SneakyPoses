package com.sneakyposes.util

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.entity.Arrow
import org.bukkit.entity.Entity
import org.bukkit.Bukkit
import java.util.UUID
import java.util.EnumSet
import java.util.Collections

object PacketManager {

    /**
     * Sends a block change to a player.
     */
    fun sendBlockChange(player: Player, location: Location, material: Material) {
        player.sendBlockChange(location, material.createBlockData())
    }

    /**
     * Spawns a fake player NPC for sleeping.
     * Returns the pair of (entityId, UUID) for the NPC.
     */
    fun spawnSleepNPC(player: Player, location: Location): Pair<Int, UUID>? {
        try {
            val craftServerClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.CraftServer")
            val minecraftServer = craftServerClass.getMethod("getServer").invoke(Bukkit.getServer())
            
            val craftWorldClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.CraftWorld")
            val serverLevel = craftWorldClass.getMethod("getHandle").invoke(location.world)
            
            val craftPlayerClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.entity.CraftPlayer")
            val entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(player)
            
            // Create GameProfile
            val gameProfileClass = Class.forName("com.mojang.authlib.GameProfile")
            val npcUuid = UUID.randomUUID()
            val gameProfile = gameProfileClass.getConstructor(UUID::class.java, String::class.java).newInstance(npcUuid, "SleepNPC")
            
            // Copy properties (skin)
            val getProfileMethod = entityPlayer.javaClass.getMethod("getGameProfile")
            val getPropertiesMethod = gameProfileClass.getMethod("getProperties")
            val npcProperties = getPropertiesMethod.invoke(gameProfile) 
            val playerProperties = getPropertiesMethod.invoke(getProfileMethod.invoke(entityPlayer))
            npcProperties.javaClass.getMethod("putAll", Class.forName("com.google.common.collect.Multimap")).invoke(npcProperties, playerProperties)

            // Get ClientInformation
            val clientInfoMethod = entityPlayer.javaClass.getMethod("clientInformation")
            val clientInfo = clientInfoMethod.invoke(entityPlayer)

            // Create ServerPlayer
            val serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer")
            val npcPlayer = serverPlayerClass.getConstructor(
                minecraftServer.javaClass.superclass, // MinecraftServer
                serverLevel.javaClass, // ServerLevel
                gameProfileClass, // GameProfile
                clientInfo.javaClass // ClientInformation
            ).newInstance(minecraftServer, serverLevel, gameProfile, clientInfo)

            // Set Pose & Location
            val spawnLoc = location.clone().add(0.0, 0.15, 0.0)
            val entityClass = Class.forName("net.minecraft.world.entity.Entity")
            val moveToMethod = entityClass.getMethod("moveTo", Double::class.java, Double::class.java, Double::class.java, Float::class.java, Float::class.java)
            moveToMethod.invoke(npcPlayer, spawnLoc.x, spawnLoc.y, spawnLoc.z, spawnLoc.yaw, 0f)

            // Set Pose to Sleeping on the object itself
            val poseClass = Class.forName("net.minecraft.world.entity.Pose")
            val sleepingPose = poseClass.getField("SLEEPING").get(null)
            serverPlayerClass.getMethod("setPose", poseClass).invoke(npcPlayer, sleepingPose)

            // Broadcast sequence
            broadcastPlayerNPCPackets(player, npcPlayer, location.clone(), npcUuid)

            Bukkit.getLogger().info("[SneakyPoses] NPC created for ${player.name}")
            return Pair(npcPlayer.javaClass.getMethod("getId").invoke(npcPlayer) as Int, npcUuid)
        } catch (e: Exception) {
            Bukkit.getLogger().severe("[SneakyPoses] Failed to spawn NPC for ${player.name}: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun broadcastPlayerNPCPackets(player: Player, npc: Any, location: Location, npcUuid: UUID) {
        val plugin = Bukkit.getPluginManager().getPlugin("SneakyPoses")!!
        try {
            val npcClass = npc.javaClass
            val craftPlayerClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.entity.CraftPlayer")
            val getHandleMethod = craftPlayerClass.getMethod("getHandle")
            
            val connectionClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl")
            val sendMethod = connectionClass.getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"))
            val connectionField = npcClass.getField("connection")

            player.world.players.forEach { viewer ->
                val viewerHandle = getHandleMethod.invoke(viewer)
                val viewerConn = connectionField.get(viewerHandle)
                connectionField.set(npc, viewerConn)

                val infoPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket")
                val actionEnum = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket\$Action")
                val noneOfMethod = java.util.EnumSet::class.java.getMethod("noneOf", Class::class.java)
                val actions = noneOfMethod.invoke(null, actionEnum) as java.util.EnumSet<*>
                val addMethod = Class.forName("java.util.Set").getMethod("add", Any::class.java)
                addMethod.invoke(actions, java.lang.Enum.valueOf(actionEnum as Class<out Enum<*>>, "ADD_PLAYER"))
                
                val infoPacket = infoPacketClass.getConstructor(java.util.EnumSet::class.java, java.util.Collection::class.java).newInstance(actions, java.util.Collections.singletonList(npc))
                sendMethod.invoke(viewerConn, infoPacket)
            }

            // 2. Delayed Spawn (Wait for client to process identity)
            val bedLoc = location.clone()
            bedLoc.y = location.world.minHeight.toDouble()
            
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                try {
                    val nmsBlockPosClass = Class.forName("net.minecraft.core.BlockPos")
                    val blockPosConstructor = nmsBlockPosClass.getConstructor(Int::class.java, Int::class.java, Int::class.java)
                    val spawnPos = blockPosConstructor.newInstance(
                        npcClass.getMethod("getX").invoke(npc).let { (it as Double).toInt() },
                        npcClass.getMethod("getY").invoke(npc).let { (it as Double).toInt() },
                        npcClass.getMethod("getZ").invoke(npc).let { (it as Double).toInt() }
                    )
                    val nmsBedPos = blockPosConstructor.newInstance(bedLoc.blockX, bedLoc.blockY, bedLoc.blockZ)

                    // Packets
                    val nmsEntityClass = Class.forName("net.minecraft.world.entity.Entity")
                    val addEntityPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket").getConstructor(nmsEntityClass, Int::class.java, nmsBlockPosClass).newInstance(npc, 0, spawnPos)
                    
                    val dataWatcher = npcClass.getMethod("getEntityData").invoke(npc)
                    val nonDefaultValues = dataWatcher.javaClass.getMethod("getNonDefaultValues").invoke(dataWatcher) as List<*>
                    val metaPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket").getConstructor(Int::class.java, List::class.java).newInstance(npcClass.getMethod("getId").invoke(npc), nonDefaultValues)
                    
                    val posMoveRotClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation")
                    val posMoveRot = posMoveRotClass.getMethod("of", nmsEntityClass).invoke(null, npc)
                    val teleportPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket").getConstructor(Int::class.java, posMoveRotClass, Set::class.java, Boolean::class.java).newInstance(npcClass.getMethod("getId").invoke(npc), posMoveRot, java.util.Collections.emptySet<Any>(), false)
                    
                    val rotateHeadPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket").getConstructor(nmsEntityClass, Byte::class.java).newInstance(npc, (location.yaw * 256f / 360f).toInt().toByte())

                    player.world.players.forEach { viewer ->
                        val viewerConn = connectionField.get(getHandleMethod.invoke(viewer))
                        connectionField.set(npc, viewerConn)
                        
                        sendMethod.invoke(viewerConn, addEntityPacket)
                        sendMethod.invoke(viewerConn, addEntityPacket) // Double add
                        sendMethod.invoke(viewerConn, metaPacket)
                        sendMethod.invoke(viewerConn, rotateHeadPacket)
                        sendMethod.invoke(viewerConn, teleportPacket)
                        sendMethod.invoke(viewerConn, teleportPacket) // Double teleport
                    }
                    Bukkit.getLogger().info("[SneakyPoses] NPC spawn packets sent for ${player.name}")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 2L)

        } catch (e: Exception) {
            Bukkit.getLogger().severe("[SneakyPoses] Error in NPC broadcast: ${e.message}")
            e.printStackTrace()
        }
    }

    fun removeSleepNPC(player: Player, npcId: Int, npcUuid: UUID, bedLoc: Location? = null) {
        val plugin = Bukkit.getPluginManager().getPlugin("SneakyPoses")!!
        
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            try {
                player.isInvisible = false
                player.world.players.forEach { it.showPlayer(plugin, player) }

                val craftPlayerClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.entity.CraftPlayer")
                val removePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket")
                val removePacket = removePacketClass.getConstructor(IntArray::class.java).newInstance(intArrayOf(npcId))

                val removeInfoPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket")
                val removeInfoPacket = removeInfoPacketClass.getConstructor(List::class.java).newInstance(java.util.Collections.singletonList(npcUuid))

                // Prepare block clear packet if bedLoc provided
                var blockClearPacket: Any? = null
                if (bedLoc != null) {
                    val nmsBlockPosClass = Class.forName("net.minecraft.core.BlockPos")
                    val blockPosConstructor = nmsBlockPosClass.getConstructor(Int::class.java, Int::class.java, Int::class.java)
                    val nmsBlockPos = blockPosConstructor.newInstance(bedLoc.blockX, bedLoc.blockY, bedLoc.blockZ)
                    
                    val blockUpdatePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket")
                    val craftBlockDataClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.block.data.CraftBlockData")
                    val nmsBlockState = craftBlockDataClass.getMethod("getState").invoke(bedLoc.block.blockData)
                    blockClearPacket = blockUpdatePacketClass.getConstructor(nmsBlockPosClass, Class.forName("net.minecraft.world.level.block.state.BlockState")).newInstance(
                        nmsBlockPos,
                        nmsBlockState
                    )
                }

                player.world.players.forEach { viewer ->
                    val viewerHandle = craftPlayerClass.getMethod("getHandle").invoke(viewer)
                    val viewerConn = viewerHandle.javaClass.getField("connection").get(viewerHandle)
                    val sendMethod = viewerConn.javaClass.getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"))
                    
                    sendMethod.invoke(viewerConn, removePacket)
                    sendMethod.invoke(viewerConn, removeInfoPacket)
                    if (blockClearPacket != null) {
                        sendMethod.invoke(viewerConn, blockClearPacket)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 1L)
    }

    /**
     * Sends a block change with specific data.
     */
    fun sendBlockChange(player: Player, location: Location, data: BlockData) {
        player.sendBlockChange(location, data)
    }

    /**
     * Spawns an invisible vehicle for sitting.
     */
    fun spawnSitVehicle(location: Location, player: Player): Entity {
        val armorStand = location.world.spawn(location, org.bukkit.entity.ArmorStand::class.java) {
            it.isInvisible = true
            it.isMarker = true // No hitbox, client-side only practically
            it.isInvulnerable = true
            it.isSilent = true
            it.setGravity(false)
            it.velocity = org.bukkit.util.Vector(0, 0, 0)
            it.setRotation(player.location.yaw, 0f)
        }
        return armorStand
    }

    /**
     * Clear block change for a player.
     */
    fun clearBlockChange(player: Player, location: Location) {
        player.sendBlockChange(location, location.block.blockData)
    }

    /**
     * Broadcasts metadata for an entity to all players.
     */
    private fun broadcastEntityMetadata(player: Player) {
        try {
            val craftPlayerClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.entity.CraftPlayer")
            val entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(player)
            val dataWatcher = entityPlayer.javaClass.getMethod("getEntityData").invoke(entityPlayer)
            
            val packDirtyMethod = dataWatcher.javaClass.getMethod("packDirty")
            val dirtyValues = packDirtyMethod.invoke(dataWatcher) ?: dataWatcher.javaClass.getMethod("getNonDefaultValues").invoke(dataWatcher)
            
            val metaPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
            val metaPacket = metaPacketClass.getConstructor(Int::class.java, List::class.java).newInstance(player.entityId, dirtyValues)

            val connectionClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl")
            val sendMethod = connectionClass.getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"))

            player.world.players.forEach { viewer ->
                val viewerHandle = craftPlayerClass.getMethod("getHandle").invoke(viewer)
                val connection = viewerHandle.javaClass.getField("connection").get(viewerHandle)
                sendMethod.invoke(connection, metaPacket)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

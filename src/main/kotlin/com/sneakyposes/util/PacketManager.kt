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
    fun spawnSleepNPC(player: Player, bedLocation: Location): Triple<Int, UUID, Any>? {
        try {
            // Get NMS Player
            val craftPlayerClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.entity.CraftPlayer")
            val entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(player)
            
            // Create GameProfile
            val gameProfileClass = Class.forName("com.mojang.authlib.GameProfile")
            val npcUuid = UUID.randomUUID()
            
            var profileName = com.sneakyposes.SneakyPoses.instance.config.getString("sleep.npc-name", "[playerName]")!!
            profileName = profileName.replace("[playerName]", player.name)
            
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    val papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                    val setPlaceholdersMethod = papiClass.getMethod("setPlaceholders", org.bukkit.OfflinePlayer::class.java, String::class.java)
                    profileName = setPlaceholdersMethod.invoke(null, player as org.bukkit.OfflinePlayer, profileName) as String
                } catch (e: Exception) {
                    // Ignore PAPI reflection errors
                }
            }
            
            if (profileName.length > 16) {
                profileName = profileName.substring(0, 16)
            }
            
            val gameProfile = gameProfileClass.getConstructor(UUID::class.java, String::class.java).newInstance(npcUuid, profileName)
            
            // Copy properties (skin)
            val getProfileMethod = entityPlayer.javaClass.getMethod("getGameProfile")
            val originalProfile = getProfileMethod.invoke(entityPlayer)
            val getPropertiesMethod = originalProfile.javaClass.getMethod("getProperties")
            val originalProperties = getPropertiesMethod.invoke(originalProfile) as com.google.common.collect.Multimap<String, Any>
            
            val newProperties = getPropertiesMethod.invoke(gameProfile) as com.google.common.collect.Multimap<String, Any>
            newProperties.putAll(originalProperties)

            // Get Server elements
            val craftServerClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.CraftServer")
            val minecraftServer = craftServerClass.getMethod("getServer").invoke(Bukkit.getServer())

            val craftWorldClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.CraftWorld")
            val serverLevel = craftWorldClass.getMethod("getHandle").invoke(bedLocation.world)

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
            val spawnLoc = bedLocation.clone().add(0.0, 0.15, 0.0)
            val entityClass = Class.forName("net.minecraft.world.entity.Entity")
            val moveToMethod = entityClass.getMethod("moveTo", Double::class.java, Double::class.java, Double::class.java, Float::class.java, Float::class.java)
            moveToMethod.invoke(npcPlayer, spawnLoc.x, spawnLoc.y, spawnLoc.z, spawnLoc.yaw, 0f)

            // Set Pose to Sleeping on the object itself
            val poseClass = Class.forName("net.minecraft.world.entity.Pose")
            val sleepingPose = poseClass.getField("SLEEPING").get(null)
            serverPlayerClass.getMethod("setPose", poseClass).invoke(npcPlayer, sleepingPose)

            // Broadcast sequence
            broadcastPlayerNPCPackets(player, npcPlayer, bedLocation.clone(), npcUuid)

            Bukkit.getLogger().info("[SneakyPoses] NPC created for ${player.name}")
            return Triple(npcPlayer.javaClass.getMethod("getId").invoke(npcPlayer) as Int, npcUuid, npcPlayer)
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
            val connectionField = Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection")
            val sendMethod = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl").getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"))

            val addActionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket\$Action")
            val playerInfoPacketConstructor = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket").getConstructor(java.util.EnumSet::class.java, java.util.Collection::class.java)

            // Calculate bed location at world bottom (standard GSit technique)
            val bedLoc = location.clone()
            bedLoc.y = location.world.minHeight.toDouble()

            // 1. Initial Registration (INFO packet)
            player.world.players.forEach { viewer ->
                try {
                    val viewerHandle = getHandleMethod.invoke(viewer)
                    val viewerConn = connectionField.get(viewerHandle)
                    
                    // Assign viewer's connection to NPC temporarily to avoid NPE during packet construction
                    connectionField.set(npc, viewerConn)
                    
                    val noneOfMethod = java.util.EnumSet::class.java.getMethod("noneOf", Class::class.java)
                    val actions = noneOfMethod.invoke(null, addActionClass) as java.util.EnumSet<*>
                    val addMethod = Class.forName("java.util.Set").getMethod("add", Any::class.java)
                    addMethod.invoke(actions, java.lang.Enum.valueOf(addActionClass as Class<out Enum<*>>, "ADD_PLAYER"))
                    
                    val infoPacket = playerInfoPacketConstructor.newInstance(
                        actions,
                        java.util.Collections.singletonList(npc)
                    )
                    sendMethod.invoke(viewerConn, infoPacket)
                } catch (e: Exception) {}
            }

            // 2. Delayed Body Spawning (2 ticks later)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                try {
                    val nmsBlockPosClass = Class.forName("net.minecraft.core.BlockPos")
                    val blockPosConstructor = nmsBlockPosClass.getConstructor(Int::class.java, Int::class.java, Int::class.java)
                    val spawnPos = blockPosConstructor.newInstance(location.blockX, location.blockY, location.blockZ)
                    val nmsBedPos = blockPosConstructor.newInstance(bedLoc.blockX, bedLoc.blockY, bedLoc.blockZ)
                    
                    val nmsEntityClass = Class.forName("net.minecraft.world.entity.Entity")
                    val addEntityPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket").getConstructor(nmsEntityClass, Int::class.java, nmsBlockPosClass).newInstance(npc, 0, spawnPos)

                    // Metadata - Set directly on NPC instead of constructing DataValues manually
                    val dataWatcher = npcClass.getMethod("getEntityData").invoke(npc)
                    val setMethod = dataWatcher.javaClass.getMethod("set", Class.forName("net.minecraft.network.syncher.EntityDataAccessor"), Any::class.java)
                    
                    // SLEEPING_POS = bedLoc (Head of bed) - index 14
                    val serializersClass = Class.forName("net.minecraft.network.syncher.EntityDataSerializers")
                    val nmsOptionalBlockPosClass = serializersClass.getField("OPTIONAL_BLOCK_POS").get(null)
                    val sleepPosAccessor = nmsOptionalBlockPosClass.javaClass.getMethod("createAccessor", Int::class.javaPrimitiveType).invoke(nmsOptionalBlockPosClass, 14)
                    val optionalClass = Class.forName("java.util.Optional")
                    val bedPosOptional = optionalClass.getMethod("of", Any::class.java).invoke(null, nmsBedPos)
                    setMethod.invoke(dataWatcher, sleepPosAccessor, bedPosOptional)
                    
                    // Skin customization (all layers) - index 17
                    val nmsByteClass = serializersClass.getField("BYTE").get(null)
                    val skinAccessor = nmsByteClass.javaClass.getMethod("createAccessor", Int::class.javaPrimitiveType).invoke(nmsByteClass, 17)
                    setMethod.invoke(dataWatcher, skinAccessor, 127.toByte())
                    
                    // Pose is already set in spawnSleepNPC via setPose, which automatically updates the dataWatcher!
                    
                    val nonDefaultValues = dataWatcher.javaClass.getMethod("getNonDefaultValues").invoke(dataWatcher) as List<*>
                    val metaPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket").getConstructor(Int::class.javaPrimitiveType, List::class.java).newInstance(npcClass.getMethod("getId").invoke(npc), nonDefaultValues)
                    
                    val rotateHeadPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket").getConstructor(nmsEntityClass, Byte::class.java).newInstance(npc, (location.yaw * 256f / 360f).toInt().toByte())

                    val nmsPositionMoveRotationClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation")
                    val nmsPositionMoveRotation = nmsPositionMoveRotationClass.getMethod("of", nmsEntityClass).invoke(null, npc)
                    val teleportPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket").getConstructor(Int::class.java, nmsPositionMoveRotationClass, java.util.Set::class.java, Boolean::class.java).newInstance(npcClass.getMethod("getId").invoke(npc), nmsPositionMoveRotation, java.util.Collections.emptySet<Any>(), false)

                    player.world.players.forEach { viewer ->
                        try {
                            val viewerHandle = getHandleMethod.invoke(viewer)
                            val viewerConn = connectionField.get(viewerHandle)
                            sendMethod.invoke(viewerConn, addEntityPacket)
                            sendMethod.invoke(viewerConn, metaPacket)
                            sendMethod.invoke(viewerConn, rotateHeadPacket)
                            sendMethod.invoke(viewerConn, teleportPacket)
                            
                            // GSit double-teleport trick (1nd teleport 1 tick later)
                            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                                try {
                                    val currentConn = connectionField.get(getHandleMethod.invoke(viewer))
                                    sendMethod.invoke(currentConn, teleportPacket)
                                } catch (e: Exception) {}
                            }, 1L)
                        } catch (e: Exception) {}
                    }
                    Bukkit.getLogger().info("[SneakyPoses] NPC spawn packets sent for ${player.name}")
                } catch (e: Exception) {
                    Bukkit.getLogger().severe("[SneakyPoses] Error in delayed NPC spawn: ${e.message}")
                    e.printStackTrace()
                }
            }, 2L)

        } catch (e: Exception) {
            Bukkit.getLogger().severe("[SneakyPoses] Error in broadcastPlayerNPCPackets: ${e.message}")
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
        val display = location.world.spawn(location, org.bukkit.entity.BlockDisplay::class.java) {
            it.isInvulnerable = true
            it.isSilent = true
            it.setGravity(false)
            it.velocity = org.bukkit.util.Vector(0, 0, 0)
            it.setRotation(player.location.yaw, 0f)
            it.addScoreboardTag("SneakyPosesSeat")
        }
        return display
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

    /**
     * Broadcasts a head rotation update for the NPC.
     */
    fun updateNPCHeadRotation(player: Player, npcEntity: Any, baseYaw: Float) {
        try {
            val flippedBaseYaw = baseYaw + 180f
            var diff = (player.location.yaw - flippedBaseYaw) % 360f
            if (diff < -180f) diff += 360f
            if (diff > 180f) diff -= 360f

            if (diff > 45f) diff = 45f
            if (diff < -45f) diff = -45f

            val finalHeadYaw = flippedBaseYaw + diff
            val fixedYaw = (finalHeadYaw * 256.0f / 360.0f).toInt().toByte()
            val entityClass = Class.forName("net.minecraft.world.entity.Entity")
            val packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket")
            val packet = packetClass.getConstructor(entityClass, Byte::class.java).newInstance(npcEntity, fixedYaw)

            val craftPlayerClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.entity.CraftPlayer")

            player.world.players.forEach { viewer ->
                if (viewer.location.distanceSquared(player.location) < 9000.0) {
                    val viewerHandle = craftPlayerClass.getMethod("getHandle").invoke(viewer)
                    val connection = viewerHandle.javaClass.getField("connection").get(viewerHandle)
                    val sendMethod = connection.javaClass.getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"))
                    sendMethod.invoke(connection, packet)
                }
            }
        } catch (e: Exception) {
            // Ignore minor sync exceptions
        }
    }
}

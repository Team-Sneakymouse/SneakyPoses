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
            // Create game profile (matching name like GSit)
            val gameProfile = gameProfileClass.getConstructor(UUID::class.java, String::class.java).newInstance(npcUuid, player.name)
            
            // Copy properties (skin, etc.)
            val getProfileMethod = entityPlayer.javaClass.getMethod("getGameProfile")
            val getPropertiesMethod = gameProfileClass.getMethod("getProperties")
            val npcProperties = getPropertiesMethod.invoke(gameProfile) 
            val playerProperties = getPropertiesMethod.invoke(getProfileMethod.invoke(entityPlayer))
            
            npcProperties.javaClass.getMethod("putAll", Class.forName("com.google.common.collect.Multimap")).invoke(npcProperties, playerProperties)

            // Create ServerPlayer (NPC)
            val clientInfoMethod = entityPlayer.javaClass.getMethod("clientInformation")
            val clientInfo = clientInfoMethod.invoke(entityPlayer)
            
            val serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer")
            // Set skin layers (Index 17)
            fun findAccessor(clazz: Class<*>, index: Int): Any? {
                var current: Class<*>? = clazz
                while (current != null) {
                    for (field in current.declaredFields) {
                        try {
                            if (java.lang.reflect.Modifier.isStatic(field.modifiers) && field.type.name.contains("EntityDataAccessor")) {
                                field.isAccessible = true
                                val accessor = field.get(null)
                                val getIndexMethod = try { accessor.javaClass.getMethod("id") } catch(e: Exception) { accessor.javaClass.getMethod("a") }
                                if (getIndexMethod.invoke(accessor) == index) return accessor
                            }
                        } catch (e: Exception) {}
                    }
                    current = current.superclass
                }
                return null
            }

            val npcPlayer = serverPlayerClass.getConstructor(
                minecraftServer.javaClass.superclass, // MinecraftServer
                serverLevel.javaClass, // ServerLevel
                gameProfileClass, // GameProfile
                clientInfo.javaClass // ClientInformation
            ).newInstance(minecraftServer, serverLevel, gameProfile, clientInfo)

            val dataWatcher = npcPlayer.javaClass.getMethod("getEntityData").invoke(npcPlayer)
            val skinLayersAccessor = findAccessor(npcPlayer.javaClass, 17)
            if (skinLayersAccessor != null) {
                val setMethod = dataWatcher.javaClass.getMethod("set", skinLayersAccessor.javaClass, Any::class.java)
                setMethod.invoke(dataWatcher, skinLayersAccessor, (0x7F).toByte()) // Enable all skin layers
            }

            val poseClass = Class.forName("net.minecraft.world.entity.Pose")
            val sleepingPose = poseClass.getField("SLEEPING").get(null)

            // CRITICAL: Sync Connection to avoid NPE in PlayerInfo packet
            val connectionField = serverPlayerClass.getField("connection")
            val playerConnection = connectionField.get(entityPlayer)
            connectionField.set(npcPlayer, playerConnection)

            // Set real player to invisible
            player.isInvisible = true

            // Set NPC Location with offset (0.15 for sleeping)
            val spawnLoc = location.clone().add(0.0, 0.15, 0.0)
            val entityClass = Class.forName("net.minecraft.world.entity.Entity")
            val moveToMethod = entityClass.getMethod("moveTo", Double::class.java, Double::class.java, Double::class.java, Float::class.java, Float::class.java)
            moveToMethod.invoke(npcPlayer, spawnLoc.x, spawnLoc.y, spawnLoc.z, spawnLoc.yaw, location.pitch)

            // NPC Data
            val bedLoc = location.clone()
            bedLoc.y = location.world.minHeight.toDouble()
            
            serverPlayerClass.getMethod("setPose", poseClass).invoke(npcPlayer, sleepingPose)
            
            // Broadcast NPC to all
            broadcastNPCPackets(player, npcPlayer, bedLoc, npcUuid)

            return Pair(npcPlayer.javaClass.getMethod("getId").invoke(npcPlayer) as Int, npcUuid)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun broadcastNPCPackets(player: Player, npc: Any, bedLoc: Location, npcUuid: UUID) {
        try {
            val npcClass = npc.javaClass
            val getHandleMethod = player.javaClass.getMethod("getHandle")
            val entityPlayer = getHandleMethod.invoke(player)

            // 1. Prepare Metadata
            val dataWatcher = npcClass.getMethod("getEntityData").invoke(npc)
            val nmsBlockPosClass = Class.forName("net.minecraft.core.BlockPos")
            val blockPosConstructor = nmsBlockPosClass.getConstructor(Int::class.java, Int::class.java, Int::class.java)
            val nmsBlockPos = blockPosConstructor.newInstance(bedLoc.blockX, bedLoc.blockY, bedLoc.blockZ)
            val optionalBedPos = java.util.Optional.of(nmsBlockPos)

            // Copy player metadata (skin layers)
            val playerDataWatcher = entityPlayer.javaClass.getMethod("getEntityData").invoke(entityPlayer)
            val nonDefaultValuesMethod = playerDataWatcher.javaClass.getMethod("getNonDefaultValues")
            val nonDefaultValues = nonDefaultValuesMethod.invoke(playerDataWatcher) as List<*>
            
            fun findAccessor(clazz: Class<*>, index: Int): Any? {
                var current: Class<*>? = clazz
                while (current != null) {
                    for (field in current.declaredFields) {
                        try {
                            if (java.lang.reflect.Modifier.isStatic(field.modifiers) && (field.type.name.contains("EntityDataAccessor") || field.type.name.contains("ajw"))) {
                                field.isAccessible = true
                                val accessor = field.get(null) ?: continue
                                val getIndexMethod = try { accessor.javaClass.getMethod("id") } catch(e: Exception) { accessor.javaClass.getMethod("a") }
                                if (getIndexMethod.invoke(accessor) == index) return accessor
                            }
                        } catch (e: Exception) {}
                    }
                    current = current.superclass
                }
                return null
            }

            // Explicitly set POSE to SLEEPING (index 6)
            val poseAccessor = findAccessor(npcClass, 6)
            if (poseAccessor != null) {
                val poseClass = Class.forName("net.minecraft.world.entity.Pose")
                val sleepingPose = poseClass.getField("SLEEPING").get(null)
                val setMethod = dataWatcher.javaClass.getMethod("set", poseAccessor.javaClass, Any::class.java)
                setMethod.invoke(dataWatcher, poseAccessor, sleepingPose)
            }

            val sleepPosAccessor = findAccessor(npcClass, 14)
            if (sleepPosAccessor != null) {
                val setMethod = dataWatcher.javaClass.getMethod("set", sleepPosAccessor.javaClass, Any::class.java)
                setMethod.invoke(dataWatcher, sleepPosAccessor, optionalBedPos)
            }

            // 2. Player Info
            val infoPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket")
            val actionEnum = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket\$Action")
            val noneOfMethod = java.util.EnumSet::class.java.getMethod("noneOf", Class::class.java)
            val actions = noneOfMethod.invoke(null, actionEnum) as java.util.EnumSet<*>
            val addMethod = Class.forName("java.util.Set").getMethod("add", Any::class.java)
            
            addMethod.invoke(actions, java.lang.Enum.valueOf(actionEnum as Class<out Enum<*>>, "ADD_PLAYER"))
            addMethod.invoke(actions, java.lang.Enum.valueOf(actionEnum, "UPDATE_LISTED"))
            addMethod.invoke(actions, java.lang.Enum.valueOf(actionEnum, "UPDATE_LATENCY"))
            addMethod.invoke(actions, java.lang.Enum.valueOf(actionEnum, "UPDATE_GAME_MODE"))
            
            val infoPacket = infoPacketClass.getConstructor(java.util.EnumSet::class.java, java.util.Collection::class.java).newInstance(actions, java.util.Collections.singletonList(npc))

            // 3. Add Entity (1.21.4 uses (Entity, int, BlockPos))
            val addEntityPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket")
            val addEntityPacket = addEntityPacketClass.getConstructor(
                Class.forName("net.minecraft.world.entity.Entity"),
                Int::class.java,
                nmsBlockPosClass
            ).newInstance(npc, 0, nmsBlockPos)

            // 4. Teleport Packet (Send twice for 1.21.4 stability)
            val teleportPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket")
            val posMoveRotClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation")
            val ofMethod = posMoveRotClass.getMethod("of", Class.forName("net.minecraft.world.entity.Entity"))
            val posMoveRot = ofMethod.invoke(null, npc)
            
            val teleportPacket = teleportPacketClass.getConstructor(Int::class.java, posMoveRotClass, Set::class.java, Boolean::class.java).newInstance(
                npcClass.getMethod("getId").invoke(npc),
                posMoveRot,
                java.util.Collections.emptySet<Any>(),
                false
            )

            // 5. Block Update (Fake Bed)
            val blockUpdatePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket")
            val bedData = Bukkit.createBlockData(Material.WHITE_BED) {
                if (it is org.bukkit.block.data.type.Bed) {
                    it.part = org.bukkit.block.data.type.Bed.Part.HEAD
                    it.facing = player.facing.oppositeFace
                }
            }
            val craftBlockDataClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.block.data.CraftBlockData")
            val nmsBlockState = craftBlockDataClass.getMethod("getState").invoke(bedData)
            val blockUpdatePacket = blockUpdatePacketClass.getConstructor(nmsBlockPosClass, Class.forName("net.minecraft.world.level.block.state.BlockState")).newInstance(
                nmsBlockPos,
                nmsBlockState
            )

            // 6. Metadata Packet
            val packDirtyMethod = dataWatcher.javaClass.getMethod("packDirty")
            val dirtyValues = packDirtyMethod.invoke(dataWatcher) as List<*>? ?: emptyList<Any>()
            val metaPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
            
            val allMetaData = (nonDefaultValues + dirtyValues).distinctBy {
                try { it!!.javaClass.getMethod("id").invoke(it) } catch(e: Exception) { 
                    try { it!!.javaClass.getMethod("a").invoke(it) } catch(e2: Exception) { it.hashCode() }
                }
            }
            val metaPacket = metaPacketClass.getConstructor(Int::class.java, List::class.java).newInstance(npcClass.getMethod("getId").invoke(npc), allMetaData)

            // 7. Head Rotation
            val rotateHeadPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket")
            val rotateHeadPacket = rotateHeadPacketClass.getConstructor(Class.forName("net.minecraft.world.entity.Entity"), Byte::class.java).newInstance(
                npc,
                (player.location.yaw * 256f / 360f).toInt().toByte()
            )

            // 8. Bundle Packet
            val bundlePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundBundlePacket")
            val packetList = listOf(infoPacket, addEntityPacket, blockUpdatePacket, teleportPacket, teleportPacket, metaPacket, rotateHeadPacket)
            val bundlePacket = bundlePacketClass.getConstructor(Iterable::class.java).newInstance(packetList)

            val connectionClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl")
            val sendMethod = connectionClass.getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"))
            val plugin = Bukkit.getPluginManager().getPlugin("SneakyPoses")!!

            player.world.players.forEach { viewer ->
                val viewerHandle = getHandleMethod.invoke(viewer)
                val connection = viewerHandle.javaClass.getField("connection").get(viewerHandle)
                
                sendMethod.invoke(connection, bundlePacket)

                // 8. Delayed Tab List Removal
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    try {
                        val removeInfoPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket")
                        val removeInfoPacket = removeInfoPacketClass.getConstructor(List::class.java).newInstance(java.util.Collections.singletonList(npcUuid))
                        sendMethod.invoke(connection, removeInfoPacket)
                    } catch(e: Exception) {}
                }, 20L) // Longer delay to ensure client loaded skin
            }
        } catch (e: Exception) {
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

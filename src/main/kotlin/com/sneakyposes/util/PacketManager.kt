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

    private const val NPC_TEAM_NAME = "sneakyposes_npc"

    /** Unique 16-char profile name used as the scoreboard team entry for an NPC UUID. */
    fun npcTeamEntry(npcUuid: UUID): String =
        npcUuid.toString().replace("-", "").take(16)

    private fun nametagHideTeam(): org.bukkit.scoreboard.Team {
        val board = Bukkit.getScoreboardManager()!!.mainScoreboard
        val existing = board.getTeam(NPC_TEAM_NAME)
        if (existing != null) return existing
        return board.registerNewTeam(NPC_TEAM_NAME).apply {
            setOption(
                org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                org.bukkit.scoreboard.Team.OptionStatus.NEVER
            )
            setOption(
                org.bukkit.scoreboard.Team.Option.COLLISION_RULE,
                org.bukkit.scoreboard.Team.OptionStatus.NEVER
            )
            setCanSeeFriendlyInvisibles(false)
        }
    }

    fun hideNpcNametag(npcUuid: UUID) {
        nametagHideTeam().addEntry(npcTeamEntry(npcUuid))
    }

    fun clearNpcNametag(npcUuid: UUID) {
        Bukkit.getScoreboardManager()?.mainScoreboard?.getTeam(NPC_TEAM_NAME)
            ?.removeEntry(npcTeamEntry(npcUuid))
    }

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

            val gameProfileClass = Class.forName("com.mojang.authlib.GameProfile")
            val npcUuid = UUID.randomUUID()

            // Profile name must be unique and non-empty: empty/odd names render as a thin nametag "slice".
            // Nametag is fully hidden via scoreboard team; skin still comes from copied properties.
            val profileName = npcTeamEntry(npcUuid)

            // Copy skin properties into an immutable PropertyMap for the NPC profile.
            val getProfileMethod = entityPlayer.javaClass.getMethod("getGameProfile")
            val originalProfile = getProfileMethod.invoke(entityPlayer)
            val originalProperties = originalProfile.javaClass.getMethod("properties").invoke(originalProfile)
            val propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap")
            val npcProperties = propertyMapClass
                .getConstructor(com.google.common.collect.Multimap::class.java)
                .newInstance(originalProperties)

            val gameProfile = gameProfileClass
                .getConstructor(UUID::class.java, String::class.java, propertyMapClass)
                .newInstance(npcUuid, profileName, npcProperties)

            // Hide nametag before any viewer receives spawn packets
            hideNpcNametag(npcUuid)

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
            val snapToMethod = entityClass.getMethod(
                "snapTo",
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )
            snapToMethod.invoke(npcPlayer, spawnLoc.x, spawnLoc.y, spawnLoc.z, spawnLoc.yaw, 0f)

            val poseClass = Class.forName("net.minecraft.world.entity.Pose")
            val sleepingPose = poseClass.getField("SLEEPING").get(null)
            serverPlayerClass.getMethod("setPose", poseClass).invoke(npcPlayer, sleepingPose)

            return Triple(npcPlayer.javaClass.getMethod("getId").invoke(npcPlayer) as Int, npcUuid, npcPlayer)
        } catch (e: Exception) {
            Bukkit.getLogger().severe("[SneakyPoses] Failed to spawn NPC for ${player.name}: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    /**
     * Sends the necessary NPC packets to a specific viewer for a posing player.
     */
    fun sendNPCPacketsToPlayer(viewer: Player, posingPlayer: Player, npc: Any, location: Location) {
        try {
            val npcClass = npc.javaClass
            val craftPlayerClass = Class.forName("${Bukkit.getServer().javaClass.packageName}.entity.CraftPlayer")
            val getHandleMethod = craftPlayerClass.getMethod("getHandle")
            val connectionField = Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection")
            val packetClass = Class.forName("net.minecraft.network.protocol.Packet")
            val sendMethod = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl")
                .getMethod("send", packetClass)

            val viewerHandle = getHandleMethod.invoke(viewer)
            val viewerConn = connectionField.get(viewerHandle)

            // 26.x clients require the full player-info init set (not only ADD_PLAYER).
            // Build Entry manually so we don't depend on the fake NPC having a real connection.
            val actionClass = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket\$Action"
            )
            val actions = java.util.EnumSet.noneOf(actionClass as Class<out Enum<*>>)
            val addAction = Class.forName("java.util.Set").getMethod("add", Any::class.java)
            listOf(
                "ADD_PLAYER",
                "UPDATE_GAME_MODE",
                "UPDATE_LISTED",
                "UPDATE_LATENCY",
                "UPDATE_DISPLAY_NAME",
                "UPDATE_HAT",
                "UPDATE_LIST_ORDER"
            ).forEach { name ->
                @Suppress("UNCHECKED_CAST")
                addAction.invoke(actions, java.lang.Enum.valueOf(actionClass as Class<out Enum<*>>, name))
            }

            val gameProfile = npcClass.getMethod("getGameProfile").invoke(npc)
            val profileUuid = npcClass.getMethod("getUUID").invoke(npc) as UUID
            val gameTypeClass = Class.forName("net.minecraft.world.level.GameType")
            val survival = gameTypeClass.getField("SURVIVAL").get(null)
            val entryClass = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket\$Entry"
            )
            val entry = entryClass.getConstructor(
                UUID::class.java,
                Class.forName("com.mojang.authlib.GameProfile"),
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                gameTypeClass,
                Class.forName("net.minecraft.network.chat.Component"),
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Class.forName("net.minecraft.network.chat.RemoteChatSession\$Data")
            ).newInstance(
                profileUuid,
                gameProfile,
                false, // listed = false (keep NPC out of tab list)
                0,
                survival,
                null,
                true, // showHat
                0,
                null // chatSession
            )

            val infoPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket")
                .getConstructor(java.util.EnumSet::class.java, entryClass)
                .newInstance(actions, entry)
            sendMethod.invoke(viewerConn, infoPacket)

            // Client-side fake bed used by the sleeping pose metadata
            val bedLoc = location.clone()
            bedLoc.y = location.world.minHeight.toDouble()
            val bedData = Material.RED_BED.createBlockData() as org.bukkit.block.data.type.Bed
            bedData.facing = PoseFacing.yawToBlockFace(location.yaw)
            bedData.part = org.bukkit.block.data.type.Bed.Part.HEAD
            sendBlockChange(viewer, bedLoc, bedData)

            // 2. Body Spawning (delay so the client can apply player-info first)
            val plugin = Bukkit.getPluginManager().getPlugin("SneakyPoses")!!
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                try {
                    val nmsBlockPosClass = Class.forName("net.minecraft.core.BlockPos")
                    val blockPosConstructor = nmsBlockPosClass.getConstructor(
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                    val nmsBedPos = blockPosConstructor.newInstance(
                        bedLoc.blockX,
                        bedLoc.blockY,
                        bedLoc.blockZ
                    )

                    val nmsEntityClass = Class.forName("net.minecraft.world.entity.Entity")
                    val entityType = Class.forName("net.minecraft.world.entity.EntityTypes")
                        .getField("PLAYER")
                        .get(null)
                    val vec3Zero = Class.forName("net.minecraft.world.phys.Vec3").getField("ZERO").get(null)
                    val entityId = npcClass.getMethod("getId").invoke(npc) as Int

                    // Precise spawn coords (avoid the BlockPos ctor, which truncates to block ints)
                    val addEntityPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket")
                        .getConstructor(
                            Int::class.javaPrimitiveType,
                            UUID::class.java,
                            Double::class.javaPrimitiveType,
                            Double::class.javaPrimitiveType,
                            Double::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                            Class.forName("net.minecraft.world.entity.EntityType"),
                            Int::class.javaPrimitiveType,
                            Class.forName("net.minecraft.world.phys.Vec3"),
                            Double::class.javaPrimitiveType
                        ).newInstance(
                            entityId,
                            profileUuid,
                            location.x,
                            location.y,
                            location.z,
                            location.pitch,
                            location.yaw,
                            entityType,
                            0,
                            vec3Zero,
                            location.yaw.toDouble()
                        )

                    val dataWatcher = npcClass.getMethod("getEntityData").invoke(npc)
                    val setMethod = dataWatcher.javaClass.getMethod(
                        "set",
                        Class.forName("net.minecraft.network.syncher.EntityDataAccessor"),
                        Any::class.java
                    )

                    Class.forName("net.minecraft.world.entity.LivingEntity")
                        .getMethod("setSleepingPos", nmsBlockPosClass)
                        .invoke(npc, nmsBedPos)

                    val poseClass = Class.forName("net.minecraft.world.entity.Pose")
                    val sleepingPose = poseClass.getField("SLEEPING").get(null)
                    nmsEntityClass.getMethod("setPose", poseClass).invoke(npc, sleepingPose)

                    val skinAccessor = Class.forName("net.minecraft.world.entity.Avatar")
                        .getField("DATA_PLAYER_MODE_CUSTOMISATION")
                        .get(null)
                    setMethod.invoke(dataWatcher, skinAccessor, 127.toByte())

                    val nonDefaultValues = dataWatcher.javaClass
                        .getMethod("getNonDefaultValues")
                        .invoke(dataWatcher) as? List<*> ?: emptyList<Any>()
                    val metaPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
                        .getConstructor(Int::class.javaPrimitiveType, List::class.java)
                        .newInstance(entityId, nonDefaultValues)

                    val rotateHeadPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket")
                        .getConstructor(nmsEntityClass, Byte::class.javaPrimitiveType)
                        .newInstance(npc, (location.yaw * 256f / 360f).toInt().toByte())

                    val nmsPositionMoveRotationClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation")
                    val nmsPositionMoveRotation = nmsPositionMoveRotationClass
                        .getMethod("of", nmsEntityClass)
                        .invoke(null, npc)
                    val teleportPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket")
                        .getConstructor(
                            Int::class.javaPrimitiveType,
                            nmsPositionMoveRotationClass,
                            java.util.Set::class.java,
                            Boolean::class.javaPrimitiveType
                        ).newInstance(
                            entityId,
                            nmsPositionMoveRotation,
                            java.util.Collections.emptySet<Any>(),
                            false
                        )

                    val currentConn = connectionField.get(getHandleMethod.invoke(viewer))
                    sendMethod.invoke(currentConn, addEntityPacket)
                    sendMethod.invoke(currentConn, metaPacket)
                    sendMethod.invoke(currentConn, rotateHeadPacket)
                    sendMethod.invoke(currentConn, teleportPacket)

                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        try {
                            val doubleCheckConn = connectionField.get(getHandleMethod.invoke(viewer))
                            sendMethod.invoke(doubleCheckConn, teleportPacket)
                        } catch (_: Exception) {
                        }
                    }, 1L)
                } catch (e: Exception) {
                    Bukkit.getLogger().severe("[SneakyPoses] Error in single-viewer NPC spawn: ${e.message}")
                    e.printStackTrace()
                }
            }, 2L)
        } catch (e: Exception) {
            Bukkit.getLogger().severe("[SneakyPoses] Error broadcasting NPC packets to ${viewer.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    fun broadcastPlayerNPCPackets(player: Player, npc: Any, location: Location) {
        val pose = PoseManager.getPose(player) ?: return
        try {
            player.world.players.forEach { viewer ->
                sendNPCPacketsToPlayer(viewer, player, npc, location)
                pose.viewerUuids.add(viewer.uniqueId)
            }
        } catch (e: Exception) {
            Bukkit.getLogger().severe("[SneakyPoses] Error in broadcastPlayerNPCPackets: ${e.message}")
            e.printStackTrace()
        }
    }

    fun removeSleepNPC(player: Player, npcId: Int, npcUuid: UUID, bedLoc: Location? = null) {
        val plugin = Bukkit.getPluginManager().getPlugin("SneakyPoses")!!
        if (!plugin.isEnabled) {
            // If plugin is disabling, just hide player and return
            player.isInvisible = false
            return
        }
        
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            try {
                player.isInvisible = false
                player.world.players.forEach { it.showPlayer(plugin, player) }
                clearNpcNametag(npcUuid)

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
     * Spawns an invisible vehicle for sitting / sleep camera mounting.
     * Marker armor stands have no hitbox and no visible model (unlike BlockDisplay / Interaction outlines).
     */
    fun spawnSitVehicle(location: Location, player: Player): Entity {
        return location.world.spawn(location, org.bukkit.entity.ArmorStand::class.java) {
            it.isVisible = false
            it.isMarker = true
            it.isInvulnerable = true
            it.isSilent = true
            it.setGravity(false)
            it.setBasePlate(false)
            it.setArms(false)
            it.isCustomNameVisible = false
            it.setRotation(player.location.yaw, 0f)
            it.addScoreboardTag("SneakyPosesSeat")
        }
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
            val playerYaw = player.location.yaw
            
            // Calculate the shortest angular difference between player yaw and NPC base yaw
            var diff = (playerYaw - baseYaw) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f

            // Mirroring logic: Treat facing towards the NPC the same as facing away
            // This ensures that looking from the front or back yields the same head swivel
            val isMirrored = java.lang.Math.abs(diff) > 90f
            var mirroredDiff = if (isMirrored) {
                if (diff > 0) diff - 180f else diff + 180f
            } else {
                diff
            }
            
            // If mirrored, the directions are inverted relative to the camera, so we flip it back
            if (isMirrored) mirroredDiff = -mirroredDiff

            // Clamp the final difference to +/- 50 degrees
            val clampedDiff = mirroredDiff.coerceIn(-50f, 50f)
            
            // Apply the swivel to the base yaw (negative offset to match camera-to-world mapping)
            val finalHeadYaw = baseYaw - clampedDiff
            val fixedYaw = (finalHeadYaw * 256f / 360f).toInt().toByte()
            
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

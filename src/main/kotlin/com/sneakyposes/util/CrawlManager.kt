package com.sneakyposes.util

import com.sneakyposes.SneakyPoses
import com.sneakyposes.listeners.PoseListenerCleanup
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import java.util.Collections

object CrawlManager {

    private val sessions = mutableMapOf<UUID, CrawlSession>()

    fun beginCrawl(player: Player, location: Location) {
        PoseManager.setPose(
            player,
            PoseData(type = PoseType.CRAWL, location = location)
        )
        start(player)
    }

    fun start(player: Player) {
        if (sessions.containsKey(player.uniqueId)) return

        val session = CrawlSession(player)
        sessions[player.uniqueId] = session
        applyCrawlPose(player)

        Bukkit.getScheduler().runTaskLater(SneakyPoses.instance, Runnable {
            if (sessions[player.uniqueId] !== session || session.finished) return@Runnable
            session.moveEnabled = true
            session.startTickTask()
            session.tick(player.location)
        }, 1L)
    }

    fun stop(player: Player) {
        sessions.remove(player.uniqueId)?.stop()
    }

    fun tick(player: Player, location: Location) {
        sessions[player.uniqueId]?.tick(location)
    }

    fun applyCrawlPose(player: Player) {
        if (!player.isValid) return
        if (player.pose != Pose.SWIMMING || !player.hasFixedPose()) {
            player.setPose(Pose.SWIMMING, true)
        }
    }

    fun clearCrawlPose(player: Player) {
        if (!player.isValid) return
        player.setPose(Pose.STANDING, false)
    }

    fun stopAll() {
        sessions.keys.toList().forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { stop(it) }
        }
    }

    private class CrawlSession(val player: Player) {
        var finished = false
        var moveEnabled = false
        var boxEntityExist = false
        private var tickTask: BukkitTask? = null
        private val boxEntity: Any = CrawlBoxEntity.create(player.location)
        private val entityId: Int = boxEntity.javaClass.getMethod("getId").invoke(boxEntity) as Int

        fun startTickTask() {
            tickTask?.cancel()
            tickTask = Bukkit.getScheduler().runTaskTimer(SneakyPoses.instance, Runnable {
                if (finished || sessions[player.uniqueId] !== this@CrawlSession) {
                    tickTask?.cancel()
                    return@Runnable
                }
                tick(player.location)
            }, 1L, 1L)
        }

        fun tick(location: Location) {
            if (finished || !moveEnabled || !checkCrawlValid()) return

            applyCrawlPose(player)

            if (shouldDisableShulker(location, player)) {
                destroyEntity()
                return
            }

            updateBoxEntity(location)
        }

        private fun updateBoxEntity(location: Location) {
            if (finished || sessions[player.uniqueId] !== this@CrawlSession) return
            if (shouldDisableShulker(location, player)) {
                destroyEntity()
                return
            }

            val locationBlock = location.block
            val blockSize = ((location.y - location.blockY) * 100).toInt()
            val playerLocation = location.clone()

            val height = if (locationBlock.boundingBox.height >= 0.4 || playerLocation.y % 0.015625 == 0.0) {
                if (player.fallDistance > 0.7f) 0 else blockSize
            } else {
                0
            }

            playerLocation.y += if (height >= 40) 1.5 else 0.5

            val shulkerClass = Class.forName("net.minecraft.world.entity.monster.Shulker")
            shulkerClass.getMethod("setRawPeekAmount", Int::class.javaPrimitiveType)
                .invoke(boxEntity, if (height >= 40) 100 - height else 0)

            if (!boxEntityExist) {
                val entityClass = Class.forName("net.minecraft.world.entity.Entity")
                entityClass.getMethod(
                    "setPos",
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType
                ).invoke(boxEntity, playerLocation.x, playerLocation.y, playerLocation.z)

                CrawlBoxEntity.sendSpawn(player, boxEntity)
                boxEntityExist = true
                CrawlBoxEntity.sendEntityData(player, boxEntity)
            } else {
                CrawlBoxEntity.sendEntityData(player, boxEntity)
                entityClassTeleport(boxEntity, playerLocation)
                CrawlBoxEntity.sendTeleport(player, boxEntity, entityId)
            }
        }

        fun stop() {
            finished = true
            tickTask?.cancel()
            clearCrawlPose(player)
            destroyEntity()
        }

        private fun destroyEntity() {
            if (!boxEntityExist) return
            CrawlBoxEntity.sendRemove(player, entityId)
            boxEntityExist = false
        }

        private fun checkCrawlValid(): Boolean {
            if (isInWater(player) || player.isFlying) {
                PoseListenerCleanup.cleanupPose(player, relocate = false)
                return false
            }
            return true
        }

        private fun entityClassTeleport(boxEntity: Any, location: Location) {
            val entityClass = Class.forName("net.minecraft.world.entity.Entity")
            entityClass.getMethod(
                "teleportTo",
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType
            ).invoke(boxEntity, location.x, location.y, location.z)
        }
    }
}

private fun shouldDisableShulker(location: Location, player: Player): Boolean {
    if (hasGsitSolidBlockAbove(location)) return true

    val velocity = player.velocity
    if (velocity.lengthSquared() <= 0.0001) return false

    val horizontal = Vector(velocity.x, 0.0, velocity.z)
    if (horizontal.lengthSquared() <= 0.0001) return false

    val ahead = location.clone().add(horizontal.clone().normalize().multiply(0.6))
    return hasGsitSolidBlockAbove(ahead)
}

private fun hasGsitSolidBlockAbove(location: Location): Boolean {
    val blockSize = ((location.y - location.blockY) * 100).toInt()
    val tickLocation = location.clone()
    tickLocation.y = tickLocation.blockY + if (blockSize >= 40) 2.49 else 1.49
    val aboveBlock = tickLocation.block
    return aboveBlock.boundingBox.contains(tickLocation.toVector()) &&
        aboveBlock.collisionShape.boundingBoxes.isNotEmpty()
}

private object CrawlBoxEntity {

    private val craftPlayerClass by lazy {
        Class.forName("${Bukkit.getServer().javaClass.packageName}.entity.CraftPlayer")
    }
    private val craftWorldClass by lazy {
        Class.forName("${Bukkit.getServer().javaClass.packageName}.CraftWorld")
    }

    fun create(location: Location): Any {
        val serverLevel = craftWorldClass.getMethod("getHandle").invoke(location.world)
        val entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType")
        val shulkerEntityType = entityTypeClass.getField("SHULKER").get(null)
        val shulkerClass = Class.forName("net.minecraft.world.entity.monster.Shulker")
        val shulker = shulkerClass.getConstructor(
            entityTypeClass,
            Class.forName("net.minecraft.world.level.Level")
        ).newInstance(shulkerEntityType, serverLevel)

        val entityClass = Class.forName("net.minecraft.world.entity.Entity")
        entityClass.getMethod(
            "setPos",
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType
        ).invoke(shulker, location.x, location.y, location.z)

        entityClass.getMethod("setInvisible", Boolean::class.javaPrimitiveType).invoke(shulker, true)
        entityClass.getMethod("setNoGravity", Boolean::class.javaPrimitiveType).invoke(shulker, true)
        entityClass.getMethod("setInvulnerable", Boolean::class.javaPrimitiveType).invoke(shulker, true)
        entityClass.getMethod("setSilent", Boolean::class.javaPrimitiveType).invoke(shulker, true)
        Class.forName("net.minecraft.world.entity.Mob")
            .getMethod("setNoAi", Boolean::class.javaPrimitiveType)
            .invoke(shulker, true)

        val directionClass = Class.forName("net.minecraft.core.Direction")
        val up = directionClass.getField("UP").get(null)
        shulkerClass.getMethod("setAttachFace", directionClass).invoke(shulker, up)

        return shulker
    }

    fun sendSpawn(player: Player, boxEntity: Any) {
        val entityClass = Class.forName("net.minecraft.world.entity.Entity")
        val addEntityPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket")
        val packet = addEntityPacketClass.getConstructor(
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
            entityClass.getMethod("getId").invoke(boxEntity),
            entityClass.getMethod("getUUID").invoke(boxEntity),
            entityClass.getMethod("getX").invoke(boxEntity),
            entityClass.getMethod("getY").invoke(boxEntity),
            entityClass.getMethod("getZ").invoke(boxEntity),
            entityClass.getMethod("getXRot").invoke(boxEntity),
            entityClass.getMethod("getYRot").invoke(boxEntity),
            entityClass.getMethod("getType").invoke(boxEntity),
            0,
            entityClass.getMethod("getDeltaMovement").invoke(boxEntity),
            entityClass.getMethod("getYHeadRot").invoke(boxEntity)
        )
        sendPacket(player, packet)
    }

    fun sendEntityData(player: Player, boxEntity: Any) {
        val entityClass = Class.forName("net.minecraft.world.entity.Entity")
        val entityId = entityClass.getMethod("getId").invoke(boxEntity) as Int
        val dataWatcher = entityClass.getMethod("getEntityData").invoke(boxEntity)
        val nonDefaultValues = dataWatcher.javaClass.getMethod("getNonDefaultValues").invoke(dataWatcher) as List<*>
        val packet = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
            .getConstructor(Int::class.javaPrimitiveType, List::class.java)
            .newInstance(entityId, nonDefaultValues)
        sendPacket(player, packet)
    }

    fun sendTeleport(player: Player, boxEntity: Any, entityId: Int) {
        val positionMoveRotationClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation")
        val positionMoveRotation = positionMoveRotationClass.getMethod("of", Class.forName("net.minecraft.world.entity.Entity"))
            .invoke(null, boxEntity)
        val packet = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket")
            .getConstructor(
                Int::class.javaPrimitiveType,
                positionMoveRotationClass,
                Set::class.java,
                Boolean::class.javaPrimitiveType
            )
            .newInstance(entityId, positionMoveRotation, Collections.emptySet<Any>(), false)
        sendPacket(player, packet)
    }

    fun sendRemove(player: Player, entityId: Int) {
        val packet = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket")
            .getConstructor(IntArray::class.java)
            .newInstance(intArrayOf(entityId))
        sendPacket(player, packet)
    }

    private fun sendPacket(player: Player, packet: Any) {
        val handle = craftPlayerClass.getMethod("getHandle").invoke(player)
        val connection = handle.javaClass.getField("connection").get(handle)
        connection.javaClass.getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"))
            .invoke(connection, packet)
    }
}

private fun isInWater(player: Player): Boolean {
    val handle = Class.forName("${Bukkit.getServer().javaClass.packageName}.entity.CraftPlayer")
        .getMethod("getHandle")
        .invoke(player)
    return handle.javaClass.getMethod("isInWater").invoke(handle) as Boolean
}

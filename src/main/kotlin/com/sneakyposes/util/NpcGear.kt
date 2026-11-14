package com.sneakyposes.util

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * The sleep NPC holds what the sleeper holds and wears their helmet. The sleeper is concealed, so their own gear
 * shows nowhere; the NPC shows it in their place. It's sent to each viewer as the NPC spawns for them, and again to
 * every viewer whenever either item changes.
 */
object NpcGear {

    private val SLOTS = arrayOf(EquipmentSlot.HAND, EquipmentSlot.HEAD)

    /** What each sleeper's NPC was last shown with. */
    private val sent = HashMap<UUID, List<ItemStack>>()

    private fun current(player: Player): List<ItemStack> = SLOTS.map { player.inventory.getItem(it).clone() }

    private fun send(viewer: Player, npc: Any, items: List<ItemStack>) {
        val entity = npc.javaClass.getMethod("getBukkitEntity").invoke(npc) as LivingEntity
        val map = HashMap<EquipmentSlot, ItemStack?>()
        SLOTS.forEachIndexed { i, slot -> map[slot] = items[i] }
        viewer.sendEquipmentChange(entity, map)
    }

    /** The NPC has just spawned for viewer: show it holding and wearing what the sleeper does now. */
    fun sendTo(viewer: Player, player: Player, npc: Any) {
        if (!viewer.isOnline || !player.isOnline) return
        try {
            send(viewer, npc, current(player))
        } catch (e: Exception) {
            player.server.logger.warning("[SneakyPoses] Couldn't show ${player.name}'s gear on their sleeping body: ${e.message}")
        }
    }

    /** Every tick while sleeping: if the held item or helmet changed, every viewer sees the new one. */
    fun tick(player: Player, pose: PoseData) {
        val npc = pose.npcEntity ?: return
        val now = current(player)
        if (sent[player.uniqueId] == now) return
        sent[player.uniqueId] = now
        try {
            for (id in pose.viewerUuids) {
                val viewer = player.server.getPlayer(id) ?: continue
                send(viewer, npc, now)
            }
        } catch (e: Exception) {
            player.server.logger.warning("[SneakyPoses] Couldn't update ${player.name}'s gear on their sleeping body: ${e.message}")
        }
    }

    fun forget(player: UUID) {
        sent.remove(player)
    }
}

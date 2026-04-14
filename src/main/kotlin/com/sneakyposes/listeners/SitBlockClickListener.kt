package com.sneakyposes.listeners

import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import com.sneakyposes.util.SitClickRules
import com.sneakyposes.util.SitPoseApplier
import com.sneakyposes.util.StairSitAnchor
import org.bukkit.Location
import org.bukkit.block.data.type.Slab
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class SitBlockClickListener : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onRightClickBlock(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        if (player.isSneaking) return
        if (!player.hasPermission("sneakyposes.command.sit")) return

        val clicked = event.clickedBlock ?: return
        val clickedData = clicked.blockData
        if (clickedData is Slab && clickedData.type == Slab.Type.DOUBLE) return
        val yOffset = SitClickRules.match(clicked.type) ?: return

        val currentPose = PoseManager.getPose(player)
        if (currentPose != null) {
            if (currentPose.type == PoseType.SIT) {
                PoseListenerCleanup.cleanupPose(player)
                event.isCancelled = true
            }
            return
        }

        val centerX = clicked.x + 0.5
        val centerZ = clicked.z + 0.5
        val (dx, dz) = StairSitAnchor.horizontalOffset(clicked)
        val anchor = Location(
            clicked.world,
            centerX + dx,
            clicked.y.toDouble(),
            centerZ + dz,
            player.location.yaw,
            player.location.pitch
        )

        SitPoseApplier.apply(player, player, anchor, yOffset)
        event.isCancelled = true
    }
}

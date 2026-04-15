package com.sneakyposes.listeners

import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import com.sneakyposes.util.SitClickRules
import com.sneakyposes.util.SitPoseApplier
import com.sneakyposes.util.StairSitAnchor
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Slab
import org.bukkit.block.data.type.Stairs
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
        if (!player.inventory.itemInMainHand.type.isAir) return
        if (!player.hasPermission("sneakyposes.command.sit")) return
        if (event.blockFace != BlockFace.UP) return

        val clicked = event.clickedBlock ?: return
        if (!clicked.getRelative(BlockFace.UP).type.isAir) return
        val clickedData = clicked.blockData
        val yOffset = SitClickRules.match(clicked.type) ?: return
        when (clickedData) {
            is Slab -> {
                if (clickedData.type != Slab.Type.BOTTOM) return
            }
            is Stairs -> {
                if (clickedData.half != Bisected.Half.BOTTOM) return
            }
            else -> return
        }

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

package com.sneakyposes.util

import org.bukkit.block.BlockFace

object PoseFacing {

    fun normalizeYaw(yaw: Float): Float {
        var y = yaw % 360f
        if (y >= 180f) y -= 360f
        if (y < -180f) y += 360f
        return y
    }

    /** Snap to the nearest cardinal yaw (0/90/180/-90). */
    fun snapYaw(yaw: Float): Float {
        val y = normalizeYaw(yaw)
        return when {
            y in -45f..45f -> 0f
            y in 45f..135f -> 90f
            y >= 135f || y < -135f -> 180f
            else -> -90f
        }
    }

    /**
     * BlockFace an entity with this yaw is looking toward.
     * Uses 0..360 wrapping so 270° (east) is not mistaken for north.
     */
    fun yawToBlockFace(yaw: Float): BlockFace {
        val y = ((yaw % 360f) + 360f) % 360f
        return when {
            y < 45f || y >= 315f -> BlockFace.SOUTH
            y < 135f -> BlockFace.WEST
            y < 225f -> BlockFace.NORTH
            else -> BlockFace.EAST
        }
    }
}

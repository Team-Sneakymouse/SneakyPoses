package com.sneakyposes.util

import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Stairs

/**
 * Horizontally adjusts a stair sit anchor toward the lower tread (quarter-block from center),
 * including corner stair shapes. Only affects bottom-half stairs; top-half uses block center.
 *
 * Offsets are in world X/Z relative to the block’s horizontal center (block min corner + 0.5).
 */
object StairSitAnchor {

    /**
     * Returns (dx, dz) to add to a block-centered anchor, or (0.0, 0.0) if unchanged.
     */
    fun horizontalOffset(block: Block): Pair<Double, Double> {
        val data = block.blockData as? Stairs ?: return 0.0 to 0.0
        if (data.half != Bisected.Half.BOTTOM) return 0.0 to 0.0

        val facing = data.facing
        if (facing == BlockFace.UP || facing == BlockFace.DOWN) return 0.0 to 0.0

        val (ndx, ndz) = offsetFacingNorth(data.shape)
        return rotateFromNorth(ndx, ndz, facing)
    }

    /**
     * Offsets for a stair that faces **north** in block space; rotated into [facing].
     * Axes: +X east, +Z south (Bukkit).
     */
    private fun offsetFacingNorth(shape: Stairs.Shape): Pair<Double, Double> =
        when (shape) {
            Stairs.Shape.STRAIGHT -> 0.0 to 0.25
            Stairs.Shape.OUTER_LEFT -> 0.25 to 0.25
            Stairs.Shape.OUTER_RIGHT -> -0.25 to 0.25
            Stairs.Shape.INNER_LEFT -> 0.25 to 0.25
            Stairs.Shape.INNER_RIGHT -> -0.25 to 0.25
        }

    /**
     * Rotates (dx, dz) CCW in the XZ plane from “stair faces north” into [facing].
     */
    private fun rotateFromNorth(dx: Double, dz: Double, facing: BlockFace): Pair<Double, Double> {
        var x = dx
        var z = dz
        repeat(quarterTurnsFromNorth(facing)) {
            val nx = -z
            val nz = x
            x = nx
            z = nz
        }
        return x to z
    }

    private fun quarterTurnsFromNorth(facing: BlockFace): Int =
        when (facing) {
            BlockFace.NORTH -> 0
            BlockFace.EAST -> 1
            BlockFace.SOUTH -> 2
            BlockFace.WEST -> 3
            else -> 0
        }
}

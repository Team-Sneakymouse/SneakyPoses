package com.sneakyposes

import com.sneakyposes.util.PoseManager
import com.sneakyposes.util.PoseType
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

/**
 * PlaceholderAPI expansion for SneakyPoses.
 *
 * Registered only when PlaceholderAPI is present (soft dependency).
 *
 * Available placeholders:
 *   %sneakyposes_sitting%   → true/false
 *   %sneakyposes_crawling%  → true/false
 *   %sneakyposes_sleeping%  → true/false
 *   %sneakyposes_posing%    → true/false (any pose active)
 */
class SneakyPosesExpansion : PlaceholderExpansion() {

    override fun getIdentifier() = SneakyPoses.IDENTIFIER
    override fun getAuthor() = "Team Sneakymouse"
    override fun getVersion() = SneakyPoses.instance.description.version
    override fun persist() = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val onlinePlayer = player?.player ?: return null
        val pose = PoseManager.getPose(onlinePlayer)

        return when (params.lowercase()) {
            "sitting"  -> (pose?.type == PoseType.SIT).toString()
            "crawling" -> (pose?.type == PoseType.CRAWL).toString()
            "sleeping" -> (pose?.type == PoseType.SLEEP).toString()
            "posing"   -> (pose != null).toString()
            else       -> null
        }
    }
}

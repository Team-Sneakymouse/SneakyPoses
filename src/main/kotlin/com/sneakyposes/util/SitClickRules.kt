package com.sneakyposes.util

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Tag
import org.bukkit.configuration.ConfigurationSection

object SitClickRules {

    private sealed interface Rule {
        val yOffset: Double
        fun matches(material: Material): Boolean
    }

    private data class MaterialRule(
        val material: Material,
        override val yOffset: Double
    ) : Rule {
        override fun matches(material: Material): Boolean = material == this.material
    }

    private data class TagRule(
        val tag: Tag<Material>,
        override val yOffset: Double
    ) : Rule {
        override fun matches(material: Material): Boolean = tag.isTagged(material)
    }

    @Volatile
    private var rules: List<Rule> = emptyList()

    fun reload(config: ConfigurationSection) {
        val newRules = mutableListOf<Rule>()

        val list = config.getMapList("sit.click-blocks")
        for ((idx, raw) in list.withIndex()) {
            val materialKey = raw["material"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val tagKey = raw["tag"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

            if ((materialKey == null) == (tagKey == null)) {
                com.sneakyposes.SneakyPoses.log("sit.click-blocks[$idx] must set exactly one of 'material' or 'tag'; skipping.")
                continue
            }

            val yOffsetAny = raw["y-offset"]
            val yOffset = (yOffsetAny as? Number)?.toDouble()
                ?: yOffsetAny?.toString()?.toDoubleOrNull()
            if (yOffset == null) {
                com.sneakyposes.SneakyPoses.log("sit.click-blocks[$idx] missing/invalid 'y-offset'; skipping.")
                continue
            }

            if (materialKey != null) {
                val material = Material.matchMaterial(materialKey)
                if (material == null) {
                    com.sneakyposes.SneakyPoses.log("sit.click-blocks[$idx] unknown material '$materialKey'; skipping.")
                    continue
                }
                newRules += MaterialRule(material, yOffset)
                continue
            }

            val nsKey = NamespacedKey.fromString(tagKey!!)
            if (nsKey == null) {
                com.sneakyposes.SneakyPoses.log("sit.click-blocks[$idx] invalid tag key '$tagKey'; skipping.")
                continue
            }

            val tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, nsKey, Material::class.java)
            if (tag == null) {
                com.sneakyposes.SneakyPoses.log("sit.click-blocks[$idx] unknown block tag '$tagKey'; skipping.")
                continue
            }

            newRules += TagRule(tag, yOffset)
        }

        rules = newRules.toList()
    }

    fun match(material: Material): Double? {
        val snapshot = rules
        for (rule in snapshot) {
            if (rule.matches(material)) return rule.yOffset
        }
        return null
    }
}

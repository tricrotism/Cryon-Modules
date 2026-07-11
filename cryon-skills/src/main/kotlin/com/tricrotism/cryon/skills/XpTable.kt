package com.tricrotism.cryon.skills

import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.entity.Boss
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster

/** How much XP each gameplay action is worth. Values are flat per action, tuned around a 100-XP first level. */
object XpTable {

    const val FISHING_XP = 15L

    val MINING: Map<Material, Long> = mapOf(
        Material.STONE to 1L, Material.DEEPSLATE to 1L,
        Material.COAL_ORE to 8L, Material.DEEPSLATE_COAL_ORE to 8L,
        Material.COPPER_ORE to 6L, Material.DEEPSLATE_COPPER_ORE to 6L,
        Material.IRON_ORE to 12L, Material.DEEPSLATE_IRON_ORE to 12L,
        Material.GOLD_ORE to 15L, Material.DEEPSLATE_GOLD_ORE to 15L,
        Material.REDSTONE_ORE to 10L, Material.DEEPSLATE_REDSTONE_ORE to 10L,
        Material.LAPIS_ORE to 12L, Material.DEEPSLATE_LAPIS_ORE to 12L,
        Material.DIAMOND_ORE to 30L, Material.DEEPSLATE_DIAMOND_ORE to 30L,
        Material.EMERALD_ORE to 35L, Material.DEEPSLATE_EMERALD_ORE to 35L,
        Material.NETHER_QUARTZ_ORE to 8L, Material.NETHER_GOLD_ORE to 10L,
        Material.ANCIENT_DEBRIS to 60L,
    )

    val WOODCUTTING: Map<Material, Long> = Tag.LOGS.values.associateWith { 6L }

    /** Ageable crops — XP only at max age, so the gate is grow-and-harvest, not spam-break. */
    val CROPS: Map<Material, Long> = mapOf(
        Material.WHEAT to 4L, Material.CARROTS to 4L, Material.POTATOES to 4L,
        Material.BEETROOTS to 5L, Material.NETHER_WART to 6L, Material.COCOA to 5L,
    )

    /** Ageless harvest blocks — anti-abuse comes from the placed-block tracker instead. */
    val GOURDS: Map<Material, Long> = mapOf(Material.MELON to 3L, Material.PUMPKIN to 3L)

    fun combatXp(victim: LivingEntity): Long = when (victim) {
        is Boss -> 250L
        is Monster -> 10L
        else -> 2L
    }
}

package com.tricrotism.cryon.shop

import com.tricrotism.cryon.common.number.PackedDecimal
import com.tricrotism.cryon.survival.api.SellModifier
import com.tricrotism.cryon.survival.api.SellService
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The sell engine: base per-item prices from `plugins/Cryon/shop/prices.yml` (written with a
 * starter table on first run — admins edit the file), times the [SellModifier] chain peers register
 * through the [SellService] contract. A modifier that throws is logged and treated as a no-op so a
 * broken peer can't take selling down.
 */
class ShopManager(private val plugin: Plugin, private val logger: Logger) : SellService {

    private var prices: Map<Material, PackedDecimal> = emptyMap()
    private val modifiers = CopyOnWriteArrayList<SellModifier>()

    override fun price(material: Material): PackedDecimal? = prices[material]

    override fun sellValue(player: Player, material: Material, amount: Int): PackedDecimal? {
        val base = prices[material] ?: return null
        var value = base * amount
        val multiplier = combinedMultiplier(player, material)
        if (multiplier != 1.0) value *= multiplier
        return value
    }

    override fun addModifier(modifier: SellModifier) {
        modifiers.add(modifier)
    }

    override fun removeModifier(modifier: SellModifier) {
        modifiers.remove(modifier)
    }

    fun priceCount(): Int = prices.size

    private fun combinedMultiplier(player: Player, material: Material): Double {
        var multiplier = 1.0
        for (modifier in modifiers) {
            try {
                multiplier *= modifier.multiplier(player, material)
            } catch (t: Throwable) { // peer code — a broken modifier must not break selling
                logger.error("Sell modifier {} failed — ignoring it", modifier.javaClass.name, t)
            }
        }
        return multiplier
    }

    fun load() {
        val file = File(File(plugin.dataFolder, "shop"), "prices.yml")
        if (!file.isFile) writeDefaults(file)
        val section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("prices") ?: return
        val loaded = mutableMapOf<Material, PackedDecimal>()
        for (key in section.getKeys(false)) {
            val material = Material.matchMaterial(key)
            val price = section.getDouble(key)
            if (material == null || price <= 0) {
                logger.warn("Skipping invalid price entry {}", key)
                continue
            }
            loaded[material] = PackedDecimal.of(price)
        }
        prices = loaded
    }

    private fun writeDefaults(file: File) {
        val yaml = YamlConfiguration()
        for ((material, price) in defaultPrices()) yaml.set("prices.${material.name}", price)
        try {
            file.parentFile.mkdirs()
            yaml.save(file)
            logger.info("Wrote the starter sell table to {}", file.path)
        } catch (e: Exception) {
            logger.error("Failed to write default prices to {}", file.path, e)
        }
    }

    /** The starter sell table — per-item Shards, tuned around crops ≈ 2 and diamonds ≈ 40. */
    private fun defaultPrices(): Map<Material, Double> = buildMap {
        // Farming
        put(Material.WHEAT, 2.0); put(Material.CARROT, 1.5); put(Material.POTATO, 1.5)
        put(Material.BEETROOT, 2.0); put(Material.NETHER_WART, 2.0); put(Material.COCOA_BEANS, 1.5)
        put(Material.MELON_SLICE, 0.5); put(Material.PUMPKIN, 2.5); put(Material.SUGAR_CANE, 1.0)
        // Mining
        put(Material.COBBLESTONE, 0.1); put(Material.STONE, 0.15); put(Material.COBBLED_DEEPSLATE, 0.1)
        put(Material.COAL, 3.0); put(Material.RAW_COPPER, 2.0); put(Material.RAW_IRON, 5.0)
        put(Material.RAW_GOLD, 8.0); put(Material.REDSTONE, 2.0); put(Material.LAPIS_LAZULI, 3.0)
        put(Material.DIAMOND, 40.0); put(Material.EMERALD, 50.0); put(Material.QUARTZ, 3.0)
        put(Material.NETHERITE_SCRAP, 150.0)
        // Woodcutting — every log/wood variant at a flat rate
        Tag.LOGS.values.forEach { put(it, 1.5) }
        // Fishing
        put(Material.COD, 4.0); put(Material.SALMON, 5.0)
        put(Material.TROPICAL_FISH, 8.0); put(Material.PUFFERFISH, 6.0)
        // Combat drops
        put(Material.ROTTEN_FLESH, 0.5); put(Material.BONE, 1.0); put(Material.STRING, 1.0)
        put(Material.SPIDER_EYE, 1.0); put(Material.GUNPOWDER, 2.5); put(Material.SLIME_BALL, 2.0)
        put(Material.ENDER_PEARL, 8.0); put(Material.BLAZE_ROD, 6.0); put(Material.GHAST_TEAR, 10.0)
        put(Material.PHANTOM_MEMBRANE, 5.0); put(Material.LEATHER, 2.0); put(Material.FEATHER, 0.5)
        // Ranching
        put(Material.BEEF, 2.0); put(Material.PORKCHOP, 2.0); put(Material.CHICKEN, 1.5)
        put(Material.MUTTON, 1.5); put(Material.RABBIT, 2.0); put(Material.EGG, 0.5)
        put(Material.INK_SAC, 1.0); put(Material.GLOW_INK_SAC, 3.0)
    }
}

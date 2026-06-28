package com.tricrotism.cryon.jumppads

import com.tricrotism.cryon.jumppads.api.JumpPadService
import com.tricrotism.cryon.jumppads.api.LaunchStrategy
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.slf4j.Logger
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Default [JumpPadService]: owns the pad set, the ordered [LaunchStrategy] chain, and the launch
 * itself. [launch] walks the chain and applies the first velocity a strategy yields, grants the rider
 * brief fall immunity, and plays the launch sound.
 *
 * Pads are persisted to `plugins/Cryon/jumppads/pads.yml` as a flat list of `world;x;y;z` keys.
 * Shared state ([pads], [fallImmunity]) is concurrent because pad triggers fire on whichever region
 * thread owns the moving player, while command edits run on the global thread.
 */
class JumpPadManager(
    private val plugin: Plugin,
    private val logger: Logger,
    private val config: JumpPadConfig,
) : JumpPadService {

    private val strategies = CopyOnWriteArrayList<LaunchStrategy>()
    private val pads = Collections.newSetFromMap(ConcurrentHashMap<BlockKey, Boolean>())

    /** Player UUID -> epoch-millis after which fall damage is charged normally again. */
    private val fallImmunity = ConcurrentHashMap<UUID, Long>()
    private val file = File(File(plugin.dataFolder, "jumppads"), "pads.yml")

    override fun addStrategy(strategy: LaunchStrategy) {
        strategies.add(strategy)
    }

    override fun removeStrategy(strategy: LaunchStrategy) {
        strategies.remove(strategy)
    }

    override fun addPad(block: Location): Boolean = pads.add(BlockKey.of(block)).also { if (it) save() }

    override fun removePad(block: Location): Boolean = pads.remove(BlockKey.of(block)).also { if (it) save() }

    override fun isPad(block: Location): Boolean = BlockKey.of(block) in pads

    override fun padCount(): Int = pads.size

    override fun launch(player: Player, origin: Location): Boolean {
        for (strategy in strategies) {
            val velocity = try {
                strategy.launch(player, origin)
            } catch (e: Exception) {
                logger.warn("Jump-pad strategy {} threw — skipping it", strategy.javaClass.simpleName, e)
                null
            } ?: continue

            player.velocity = velocity
            grantFallImmunity(player)
            player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.2f)
            return true
        }
        return false
    }

    private fun grantFallImmunity(player: Player) {
        fallImmunity[player.uniqueId] = System.currentTimeMillis() + config.fallImmunityMs
    }

    /**
     * Consume any active fall-immunity grant for [player]: true if the landing fall damage should be
     * cancelled. Single-shot — the grant is cleared whether or not it was still in its window, so a
     * later unrelated fall isn't absorbed.
     */
    fun consumeFallImmunity(player: Player): Boolean {
        val expiry = fallImmunity.remove(player.uniqueId) ?: return false
        return System.currentTimeMillis() <= expiry
    }

    /** Drop any transient state for a player who logged off. */
    fun forget(uuid: UUID) {
        fallImmunity.remove(uuid)
    }

    /** Read persisted pads. Call once in `onLoad`. */
    fun load() {
        if (!file.isFile) return
        YamlConfiguration.loadConfiguration(file)
            .getStringList("pads")
            .mapNotNull(BlockKey::parse)
            .forEach(pads::add)
    }

    private fun save() {
        try {
            file.parentFile.mkdirs()
            YamlConfiguration().apply { set("pads", pads.map(BlockKey::serialize)) }.save(file)
        } catch (e: Exception) {
            logger.error("Failed to save jump pads to {}", file.path, e)
        }
    }

    /** A world-qualified block coordinate — the identity of a pad, independent of any loaded chunk. */
    private data class BlockKey(val world: UUID, val x: Int, val y: Int, val z: Int) {
        fun serialize(): String = "$world;$x;$y;$z"

        companion object {
            fun of(location: Location): BlockKey =
                BlockKey(location.world.uid, location.blockX, location.blockY, location.blockZ)

            fun parse(raw: String): BlockKey? {
                val parts = raw.split(';')
                if (parts.size != 4) return null
                return try {
                    BlockKey(UUID.fromString(parts[0]), parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
    }
}

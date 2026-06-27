package com.tricrotism.cryon.spawn

import com.tricrotism.cryon.visibility.api.VisibilityRule
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.slf4j.Logger
import java.io.File

/**
 * Holds the spawn point (persisted to `plugins/Cryon/spawn/spawn.yml`) and contributes the visibility
 * [rule] this module plugs into the engine: players standing in the spawn region don't render each
 * other, so a busy hub stays uncluttered. Everything outside spawn is unaffected.
 */
class Spawn(private val plugin: Plugin, private val logger: Logger) {

    @Volatile
    private var location: Location? = null
    private val file = File(File(plugin.dataFolder, "spawn"), "spawn.yml")

    /** The visibility rule — register once with the engine; both endpoints must be in spawn to hide. */
    val rule: VisibilityRule = VisibilityRule { viewer, target -> isAtSpawn(viewer) && isAtSpawn(target) }

    fun location(): Location? = location?.clone()

    fun isSet(): Boolean = location != null

    fun set(location: Location) {
        this.location = location.clone()
        save()
    }

    /** True if [player] stands in the spawn region — same world, within [RADIUS] blocks. */
    fun isAtSpawn(player: Player): Boolean {
        val spawn = location ?: return false
        if (player.world.uid != spawn.world?.uid) return false
        return player.location.distanceSquared(spawn) <= RADIUS * RADIUS
    }

    fun load() {
        if (!file.isFile) return
        location = YamlConfiguration.loadConfiguration(file).getLocation("spawn")
    }

    private fun save() {
        try {
            file.parentFile.mkdirs()
            YamlConfiguration().apply { set("spawn", location) }.save(file)
        } catch (e: Exception) {
            logger.error("Failed to save spawn to {}", file.path, e)
        }
    }

    companion object {
        /** Radius of the "spawn region" the visibility rule applies in. */
        private const val RADIUS = 16.0
    }
}

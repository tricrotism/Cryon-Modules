package com.tricrotism.cryon.jumppads

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.slf4j.Logger
import java.io.File

/**
 * Tunables for the jump-pad feature, read once from `plugins/Cryon/jumppads/config.yml` in `onLoad`
 * (hand-rolled YAML — there's no module-config framework yet; see the root `CLAUDE.md` → *Config*).
 * The file is written with these defaults the first time the module loads so it's there to edit.
 *
 * The physics is a deliberately simple constant-gravity ballistic, not a Minecraft-exact simulation —
 * air drag means the targeted arc is approximate. [ticksPerBlock] and [gravity] are the dials to tune
 * if pads consistently over- or under-shoot on your server.
 */
data class JumpPadConfig(
    /** Speed of a directional (no-target) launch along the look vector, in blocks/tick. */
    val directionalPower: Double,
    /** Minimum upward velocity of a directional launch, so a flat look still gives a hop. */
    val minVertical: Double,
    /** Gravity used to solve the targeted arc, in blocks/tick². Minecraft player gravity ≈ 0.08. */
    val gravity: Double,
    /** Flight time of a targeted arc per block of horizontal distance, in ticks. Higher = floatier. */
    val ticksPerBlock: Double,
    /** Lower clamp on targeted flight time (ticks) so very short hops still arc. */
    val minFlightTicks: Double,
    /** Upper clamp on targeted flight time (ticks) so long shots don't hang forever. */
    val maxFlightTicks: Double,
    /** How far to ray-trace for a target block the player is looking at, in blocks. */
    val targetRange: Double,
    /** Minimum horizontal distance to a target; closer than this falls back to a directional launch. */
    val minTargetDistance: Double,
    /** Per-player re-trigger guard in millis, so standing on a pad fires once, not every tick. */
    val cooldownMs: Long,
    /** How long after a launch fall damage is waived, in millis (the launch shouldn't hurt the rider). */
    val fallImmunityMs: Long,
) {
    private fun write(file: File, logger: Logger) {
        try {
            file.parentFile.mkdirs()
            YamlConfiguration().apply {
                set("launch.directional-power", directionalPower)
                set("launch.min-vertical", minVertical)
                set("launch.gravity", gravity)
                set("launch.ticks-per-block", ticksPerBlock)
                set("launch.min-flight-ticks", minFlightTicks)
                set("launch.max-flight-ticks", maxFlightTicks)
                set("target.range", targetRange)
                set("target.min-distance", minTargetDistance)
                set("trigger.cooldown-ms", cooldownMs)
                set("trigger.fall-immunity-ms", fallImmunityMs)
            }.save(file)
        } catch (e: Exception) {
            logger.error("Failed to write default jump-pad config to {}", file.path, e)
        }
    }

    companion object {
        private val DEFAULT = JumpPadConfig(
            directionalPower = 1.4,
            minVertical = 0.4,
            gravity = 0.08,
            ticksPerBlock = 1.6,
            minFlightTicks = 8.0,
            maxFlightTicks = 60.0,
            targetRange = 48.0,
            minTargetDistance = 3.0,
            cooldownMs = 250,
            fallImmunityMs = 8_000,
        )

        /** Read the config, materializing it with defaults on first run so server owners can edit it. */
        fun load(plugin: Plugin, logger: Logger): JumpPadConfig {
            val file = File(File(plugin.dataFolder, "jumppads"), "config.yml")
            val yaml = YamlConfiguration.loadConfiguration(file) // empty config if the file is absent
            val config = JumpPadConfig(
                directionalPower = yaml.getDouble("launch.directional-power", DEFAULT.directionalPower),
                minVertical = yaml.getDouble("launch.min-vertical", DEFAULT.minVertical),
                gravity = yaml.getDouble("launch.gravity", DEFAULT.gravity),
                ticksPerBlock = yaml.getDouble("launch.ticks-per-block", DEFAULT.ticksPerBlock),
                minFlightTicks = yaml.getDouble("launch.min-flight-ticks", DEFAULT.minFlightTicks),
                maxFlightTicks = yaml.getDouble("launch.max-flight-ticks", DEFAULT.maxFlightTicks),
                targetRange = yaml.getDouble("target.range", DEFAULT.targetRange),
                minTargetDistance = yaml.getDouble("target.min-distance", DEFAULT.minTargetDistance),
                cooldownMs = yaml.getLong("trigger.cooldown-ms", DEFAULT.cooldownMs),
                fallImmunityMs = yaml.getLong("trigger.fall-immunity-ms", DEFAULT.fallImmunityMs),
            )
            if (!file.isFile) config.write(file, logger)
            return config
        }
    }
}

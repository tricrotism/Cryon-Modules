package com.tricrotism.cryon.jumppads.strategy

import com.tricrotism.cryon.jumppads.JumpPadConfig
import com.tricrotism.cryon.jumppads.api.LaunchStrategy
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.sqrt

/**
 * The "launch me at what I'm looking at" behavior. Ray-traces from the player's eyes; if they're
 * looking at a block within range, it solves a ballistic arc that drops them onto the top of that
 * block instead of flinging them off in a vaguely-right direction.
 *
 * Returns null — deferring to the next strategy (the directional fallback) — when the player is
 * looking at nothing, or at a block so close it isn't really a target ([JumpPadConfig.minTargetDistance]).
 *
 * The arc is a constant-gravity solution: pick a flight time that scales with horizontal distance,
 * then the velocity that reaches the target in that time is fully determined. Minecraft air drag makes
 * this approximate; tune [JumpPadConfig.ticksPerBlock] / [JumpPadConfig.gravity] if it over/undershoots.
 */
class TargetedLaunchStrategy(private val config: JumpPadConfig) : LaunchStrategy {

    override fun launch(player: Player, origin: Location): Vector? {
        val hit = player.rayTraceBlocks(config.targetRange) ?: return null
        val block = hit.hitBlock ?: return null

        // Aim for the top-center of the block so the rider lands on it rather than in its side.
        val target = block.location.add(0.5, 1.0, 0.5)
        val dx = target.x - origin.x
        val dy = target.y - origin.y
        val dz = target.z - origin.z

        val horizontal = sqrt(dx * dx + dz * dz)
        // Looking at our own feet — no meaningful arc; let the directional fallback handle it.
        if (horizontal < config.minTargetDistance) return null

        val t = (horizontal * config.ticksPerBlock).coerceIn(config.minFlightTicks, config.maxFlightTicks)
        val vx = dx / t
        val vz = dz / t
        // Vertical: reach dy in t ticks while fighting gravity over the flight (½·g·t is the lift lost).
        val vy = dy / t + 0.5 * config.gravity * t
        return Vector(vx, vy, vz)
    }
}

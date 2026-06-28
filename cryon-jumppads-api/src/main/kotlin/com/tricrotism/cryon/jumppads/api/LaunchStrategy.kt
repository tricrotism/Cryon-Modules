package com.tricrotism.cryon.jumppads.api

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * One way to launch a player off a jump pad. The [JumpPadService] consults its registered strategies
 * **in order** and uses the first one that returns a non-null velocity — so ordering is priority.
 *
 * The two built-ins model the default behaviour: a *targeted* strategy that arcs the player onto the
 * block they're looking at (and returns null when they're looking at nothing), followed by a
 * *directional* strategy that simply flings them along their look vector. Drop your own strategy in
 * front to special-case a pad (e.g. always launch straight up in a particular region) with no change
 * to the engine — that's the extension seam.
 *
 * Keep [launch] cheap and side-effect-free: it runs on the server/region thread the moment a player
 * steps on a pad. Return the velocity to apply, or null to defer to the next strategy.
 */
fun interface LaunchStrategy {
    /** The velocity to fling [player] with from [origin] (the pad), or null to let the next strategy try. */
    fun launch(player: Player, origin: Location): Vector?
}

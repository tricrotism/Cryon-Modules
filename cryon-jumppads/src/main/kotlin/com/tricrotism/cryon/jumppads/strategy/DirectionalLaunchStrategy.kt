package com.tricrotism.cryon.jumppads.strategy

import com.tricrotism.cryon.jumppads.JumpPadConfig
import com.tricrotism.cryon.jumppads.api.LaunchStrategy
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * The default fallback: fling the player along their look vector at a fixed power. This is the plain
 * "launch pad" behaviour used whenever there's no block being targeted. A floor on the vertical
 * component ([JumpPadConfig.minVertical]) guarantees even a dead-flat look still pops the player up,
 * so the pad never feels dead.
 *
 * It never returns null — being last in the chain, it's the guaranteed launch.
 */
class DirectionalLaunchStrategy(private val config: JumpPadConfig) : LaunchStrategy {

    override fun launch(player: Player, origin: Location): Vector {
        val velocity = player.location.direction.multiply(config.directionalPower)
        if (velocity.y < config.minVertical) velocity.y = config.minVertical
        return velocity
    }
}

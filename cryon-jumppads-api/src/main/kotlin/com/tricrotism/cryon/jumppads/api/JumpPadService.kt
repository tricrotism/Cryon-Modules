package com.tricrotism.cryon.jumppads.api

import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * The jump-pad engine, published into the Cryon `ServiceRegistry` by the `cryon-jumppads` module. It
 * owns the set of pad blocks and the ordered [LaunchStrategy] chain that decides *how* a player is
 * flung when they step on one.
 *
 * This interface lives in the standalone `cryon-jumppads-api` jar (dropped in `plugins/Cryon/api/`),
 * so **any** feature repo can extend the mechanic without touching the engine:
 * `services.find(JumpPadService::class)` in `onEnable`, then [addStrategy] a custom launch behaviour
 * or [addPad] pads programmatically. Whatever you add, remove again on disable.
 */
interface JumpPadService {

    /**
     * Register a launch behaviour. Strategies are consulted in registration order and the first to
     * return a velocity wins, so add yours before the defaults to take precedence. Remember to
     * [removeStrategy] on disable.
     */
    fun addStrategy(strategy: LaunchStrategy)

    /** Remove a previously-added strategy (by identity). */
    fun removeStrategy(strategy: LaunchStrategy)

    /** Mark the block at [block] as a jump pad. True if it was newly added (false if already a pad). */
    fun addPad(block: Location): Boolean

    /** Unmark the block at [block]. True if it was a pad and is now removed. */
    fun removePad(block: Location): Boolean

    /** Whether the block at [block] is a jump pad. */
    fun isPad(block: Location): Boolean

    /** How many pads are currently registered. */
    fun padCount(): Int

    /**
     * Run the strategy chain for [player] launching from [origin] and apply the result. True if a
     * strategy launched them. Called by the engine when a player steps on a pad, but exposed so other
     * modules can trigger a launch directly (e.g. from their own block).
     */
    fun launch(player: Player, origin: Location): Boolean
}

package com.tricrotism.cryon.visibility.api

import org.bukkit.entity.Player

/**
 * The player-visibility engine, published into the Cryon `ServiceRegistry` by the `cryon-visibility`
 * module. It decides who can see whom by consulting the registered [VisibilityRule]s and applies the
 * result through Bukkit's per-player `hidePlayer`/`showPlayer` tracker — players stay in the same
 * world, they just aren't rendered for viewers a rule hides them from.
 *
 * This interface lives in the standalone `cryon-visibility-api` jar (dropped in `plugins/Cryon/api/`),
 * so **any** feature repo can consume it: `services.find(VisibilityService::class)` in `onEnable`,
 * then add a rule or query visibility. Vanish is the first built-in rule; a separate module (e.g.
 * `cryon-spawn`) can register its own rule the same way.
 */
interface VisibilityService {

    /** Whether [viewer] should currently see [target] — true unless some [VisibilityRule] hides it. */
    fun canSee(viewer: Player, target: Player): Boolean

    /** Add a source of invisibility. Call [refresh]/[refreshAll] afterwards to apply it. */
    fun addRule(rule: VisibilityRule)

    /** Remove a previously-added rule (by identity) — do this on disable. Call [refreshAll] after. */
    fun removeRule(rule: VisibilityRule)

    /** Re-evaluate visibility between [player] and every other online player. */
    fun refresh(player: Player)

    /** Re-evaluate visibility across every online player pair (after bulk rule/state changes). */
    fun refreshAll()
}

package com.tricrotism.cryon.visibility.api

import org.bukkit.entity.Player

/**
 * One reason a player might be hidden from another. Rules are consulted by [VisibilityService.canSee]:
 * a target is visible to a viewer only if **no** rule hides it. Vanish ships as the first rule; an
 * area-based "don't render players in this region" rule would be a second, added via
 * [VisibilityService.addRule] with no change to the engine.
 *
 * Keep [hides] cheap and side-effect-free — it runs for every viewer/target pair on each refresh.
 */
fun interface VisibilityRule {
    /** True if this rule says [target] should be hidden from [viewer]. */
    fun hides(viewer: Player, target: Player): Boolean
}

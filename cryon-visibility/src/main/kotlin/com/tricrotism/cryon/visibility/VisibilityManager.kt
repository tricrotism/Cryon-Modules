package com.tricrotism.cryon.visibility

import com.tricrotism.cryon.visibility.api.VisibilityRule
import com.tricrotism.cryon.visibility.api.VisibilityService
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Default [VisibilityService]: holds the rule list and drives the hide/show passes. A player sees a
 * target unless some rule hides it; [applyPair] reconciles that decision with the viewer's current
 * tracker state so we only call hide/show when it actually changes.
 *
 * Every Bukkit call here is main-thread API, and all callers (commands, join/quit, rule-state
 * changes) are already on the server thread, so no scheduler hop is needed. On Folia, route the
 * passes through the entity scheduler.
 */
class VisibilityManager(private val plugin: Plugin) : VisibilityService {

    private val rules = CopyOnWriteArrayList<VisibilityRule>()

    override fun addRule(rule: VisibilityRule) {
        rules.add(rule)
    }

    override fun removeRule(rule: VisibilityRule) {
        rules.remove(rule)
    }

    override fun canSee(viewer: Player, target: Player): Boolean {
        if (viewer.uniqueId == target.uniqueId) return true
        return rules.none { it.hides(viewer, target) }
    }

    override fun refresh(player: Player) {
        for (other in plugin.server.onlinePlayers) {
            if (other.uniqueId == player.uniqueId) continue
            applyPair(other, player)
            applyPair(player, other)
        }
    }

    override fun refreshAll() {
        val online = plugin.server.onlinePlayers
        for (viewer in online) {
            for (target in online) {
                if (viewer.uniqueId != target.uniqueId) applyPair(viewer, target)
            }
        }
    }

    /** Unconditionally show every player to every other — used on disable so nobody is left hidden. */
    fun revealAll() {
        val online = plugin.server.onlinePlayers
        for (viewer in online) {
            for (target in online) {
                if (viewer.uniqueId != target.uniqueId) viewer.showPlayer(plugin, target)
            }
        }
    }

    private fun applyPair(viewer: Player, target: Player) {
        val shouldSee = canSee(viewer, target)
        val currentlySees = viewer.canSee(target)
        when {
            shouldSee && !currentlySees -> viewer.showPlayer(plugin, target)
            !shouldSee && currentlySees -> viewer.hidePlayer(plugin, target)
        }
    }
}

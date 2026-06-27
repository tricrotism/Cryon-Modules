package com.tricrotism.cryon.visibility.vanish

import com.tricrotism.cryon.visibility.api.VisibilityRule
import com.tricrotism.cryon.visibility.api.VisibilityService
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Vanish: the first source of invisibility plugged into the [VisibilityService]. Holds the network's
 * vanished players and contributes [rule] — "hidden from any viewer without [PERM_SEE]". Local toggles
 * apply the vanish physics (no mob targeting/spawning/sleep-blocking), re-render, and fire
 * [onLocalChange] so a sync layer can broadcast them; [applyNetwork] applies a toggle pushed from
 * another server. The set isn't per-login — it holds whoever is vanished until they un-vanish or the
 * server restarts, so vanish survives relogs and server switches.
 */
class Vanish(private val visibility: VisibilityService, private val plugin: Plugin) {

    private val vanished: MutableSet<UUID> = Collections.newSetFromMap(ConcurrentHashMap())
    private var onLocalChange: ((UUID, Boolean) -> Unit)? = null

    /** The visibility rule this subsystem contributes — register it once with the engine. */
    val rule: VisibilityRule = VisibilityRule { viewer, target ->
        target.uniqueId in vanished && !viewer.hasPermission(PERM_SEE)
    }

    fun isVanished(player: Player): Boolean = player.uniqueId in vanished

    fun vanished(): Set<UUID> = vanished.toSet()

    /** Register a callback fired on **local** toggles (uuid, vanished) — a sync layer broadcasts it. */
    fun onLocalChange(callback: ((UUID, Boolean) -> Unit)?) {
        onLocalChange = callback
    }

    fun toggle(player: Player): Boolean {
        val now = player.uniqueId !in vanished
        setVanished(player, now)
        return now
    }

    /** Toggle from this server: apply, re-render, and broadcast to peers. */
    fun setVanished(player: Player, vanished: Boolean) {
        apply(player, vanished)
        onLocalChange?.invoke(player.uniqueId, vanished)
    }

    /** Apply a toggle pushed from another server — update the set, and re-render if the player is here. */
    fun applyNetwork(uuid: UUID, vanished: Boolean) {
        val player = plugin.server.getPlayer(uuid)
        if (player == null) {
            if (vanished) this.vanished.add(uuid) else this.vanished.remove(uuid)
            return
        }
        apply(player, vanished)
    }

    private fun apply(player: Player, vanished: Boolean) {
        if (vanished) this.vanished.add(player.uniqueId) else this.vanished.remove(player.uniqueId)
        player.isSleepingIgnored = vanished
        player.affectsSpawning = !vanished
        if (vanished) clearNearbyTargets(player)
        visibility.refresh(player)
    }

    private fun clearNearbyTargets(player: Player) {
        for (entity in player.getNearbyEntities(NEARBY_MOBS, NEARBY_MOBS, NEARBY_MOBS)) {
            if (entity is Mob && entity.target?.uniqueId == player.uniqueId) entity.target = null
        }
    }

    companion object {
        const val PERM_SEE = "cryon.vanish.see"
        private const val NEARBY_MOBS = 48.0
    }
}

package com.tricrotism.cryon.jumppads

import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.module.ModuleContext
import com.tricrotism.cryon.jumppads.api.JumpPadService
import com.tricrotism.cryon.jumppads.strategy.DirectionalLaunchStrategy
import com.tricrotism.cryon.jumppads.strategy.TargetedLaunchStrategy
import com.tricrotism.cryon.paper.api.PaperModule
import com.tricrotism.cryon.paper.api.event.Events
import com.tricrotism.cryon.paper.api.event.Subscription
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Jump pads. A pad is a block; stepping onto it launches you. **Where** you launch is decided by the
 * [JumpPadService]'s strategy chain, which this module seeds with the two defaults in priority order:
 *
 *  1. [TargetedLaunchStrategy] — if you're looking at a block, arc you onto it.
 *  2. [DirectionalLaunchStrategy] — otherwise, fling you along your look vector.
 *
 * Both the service and the chain are open for other modules to extend: `find(JumpPadService)` then
 * `addStrategy(...)` in front of the defaults (see the root `CLAUDE.md` → *Talking to another module*).
 *
 * Triggering is a [PlayerMoveEvent] that fires once per block change, gated by a short per-player
 * cooldown so a single step launches once rather than every tick. A launched rider is given brief fall
 * immunity so the landing doesn't punish them.
 */
class JumpPadModule : PaperModule() {

    override val id = "jumppads"

    private lateinit var config: JumpPadConfig
    private lateinit var manager: JumpPadManager
    private val targeted by lazy { TargetedLaunchStrategy(config) }
    private val directional by lazy { DirectionalLaunchStrategy(config) }

    /** Player UUID -> epoch-millis of their last pad trigger; the re-trigger cooldown. */
    private val lastTrigger = ConcurrentHashMap<UUID, Long>()
    private val subscriptions = mutableListOf<Subscription>()

    override fun onLoad(context: ModuleContext) {
        super.onLoad(context) // captures the PaperModuleContext — keep first
        config = JumpPadConfig.load(plugin, logger)
        manager = JumpPadManager(plugin, logger, config)
        manager.load()
        // Default chain: aim at a looked-at block, else fling along the look vector. Order is priority.
        manager.addStrategy(targeted)
        manager.addStrategy(directional)
        services.register(JumpPadService::class, manager)
        registerCommands(JumpPadCommands(manager, services.get(MessageService::class)))
        logger.info("Jump pads loaded ({} pads)", manager.padCount())
    }

    override fun onEnable() {
        subscriptions += Events.subscribe(PlayerMoveEvent::class.java)
            .filter { it.hasChangedBlock() } // only when they actually step to a new block
            .handler(::onMove)

        // The launch shouldn't hurt the rider — waive the landing's fall damage within the grace window.
        subscriptions += Events.subscribe(EntityDamageEvent::class.java)
            .filter { it.cause == DamageCause.FALL }
            .filter { it.entity is Player }
            .handler { if (manager.consumeFallImmunity(it.entity as Player)) it.isCancelled = true }

        subscriptions += Events.subscribe(PlayerQuitEvent::class.java).handler {
            lastTrigger.remove(it.player.uniqueId)
            manager.forget(it.player.uniqueId)
        }
    }

    override fun onDisable() {
        super.onDisable()
        subscriptions.forEach(Subscription::unregister)
        subscriptions.clear()
        lastTrigger.clear()
        // Pull our strategies back out so a disabled module stops affecting any launches.
        manager.removeStrategy(targeted)
        manager.removeStrategy(directional)
    }

    private fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val origin = padOrigin(player) ?: return

        val now = System.currentTimeMillis()
        val last = lastTrigger[player.uniqueId]
        if (last != null && now - last < config.cooldownMs) return
        lastTrigger[player.uniqueId] = now

        manager.launch(player, origin)
    }

    /** Launch origin if [player] is standing on (or in) a pad block, else null. */
    private fun padOrigin(player: Player): Location? {
        val feet = player.location.block
        if (manager.isPad(feet.location)) return player.location // plate-style pad in the feet cell
        if (manager.isPad(feet.getRelative(BlockFace.DOWN).location)) return player.location // standing on top
        return null
    }
}

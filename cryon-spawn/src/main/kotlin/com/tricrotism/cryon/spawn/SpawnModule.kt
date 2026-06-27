package com.tricrotism.cryon.spawn

import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.module.ModuleContext
import com.tricrotism.cryon.paper.api.PaperModule
import com.tricrotism.cryon.paper.api.event.Events
import com.tricrotism.cryon.paper.api.event.Subscription
import com.tricrotism.cryon.visibility.api.VisibilityService
import org.bukkit.event.player.PlayerMoveEvent

/**
 * A starter spawn feature — and the worked example of one feature repo consuming another's service.
 * `/spawn` teleports, `/spawn set` sets the point. **Visibility integration is optional:** if the
 * `cryon-visibility` module is installed, this resolves its [VisibilityService] from the registry and
 * registers a rule hiding the spawn crowd from itself; if it isn't, spawn still works, just without
 * the hiding. That `services.find(...) ?: return`-style guard is the cross-module contract — both
 * repos `compileOnly` `cryon-visibility-api`, whose jar lives in `plugins/Cryon/api/` at runtime.
 */
class SpawnModule : PaperModule() {

    override val id = "spawn"

    private lateinit var spawn: Spawn
    private var visibility: VisibilityService? = null
    private val subscriptions = mutableListOf<Subscription>()

    override fun onLoad(context: ModuleContext) {
        super.onLoad(context) // captures the PaperModuleContext — keep first
        spawn = Spawn(plugin, logger)
        spawn.load()
        registerCommands(SpawnCommands(spawn, services.get(MessageService::class)))
        logger.info("Spawn loaded")
    }

    override fun onEnable() {
        // Optional peer: the visibility engine. find (not get) — it's a separate repo that may be
        // absent. onLoad ran for every module before this, so if it's installed, it's registered.
        val vis = services.find(VisibilityService::class)
        visibility = vis
        if (vis == null) {
            logger.info("cryon-visibility not present — spawn hiding disabled")
            return
        }

        vis.addRule(spawn.rule)
        // Re-render a player whenever they cross a block boundary (i.e. step in/out of spawn).
        subscriptions += Events.subscribe(PlayerMoveEvent::class.java)
            .filter { it.hasChangedBlock() }
            .handler { vis.refresh(it.player) }
        vis.refreshAll() // apply to anyone already standing in spawn
        logger.info("Hooked the visibility engine — players in spawn are hidden from each other")
    }

    override fun onDisable() {
        super.onDisable()
        subscriptions.forEach(Subscription::unregister)
        subscriptions.clear()
        // Pull our rule back out so a disabled spawn module stops affecting visibility, then re-render.
        visibility?.let {
            it.removeRule(spawn.rule)
            it.refreshAll()
        }
        visibility = null
    }
}

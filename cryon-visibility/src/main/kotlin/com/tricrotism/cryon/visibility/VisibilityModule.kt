package com.tricrotism.cryon.visibility

import com.tricrotism.cryon.common.data.Database
import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.module.ModuleContext
import com.tricrotism.cryon.common.net.Messenger
import com.tricrotism.cryon.common.text.CommonMessages
import com.tricrotism.cryon.paper.api.PaperModule
import com.tricrotism.cryon.paper.api.event.Events
import com.tricrotism.cryon.paper.api.event.Subscription
import com.tricrotism.cryon.paper.api.extension.resolvedLocale
import com.tricrotism.cryon.visibility.api.VisibilityService
import com.tricrotism.cryon.visibility.vanish.Vanish
import com.tricrotism.cryon.visibility.vanish.VanishCommands
import com.tricrotism.cryon.visibility.vanish.VanishStore
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Player-visibility feature. Publishes [VisibilityService] — a rule-driven engine that hides players
 * from viewers who shouldn't see them (same world, just not rendered) — and ships **vanish** as the
 * first rule. A future "hide players in an area" system plugs in as another `VisibilityRule` with no
 * change to the engine.
 *
 * Vanish-specific behaviour (mob/pickup physics, suppressed join/quit broadcasts, blocked private
 * messages) lives behind its own [Subscription]s gated on the vanish state. When Redis is configured,
 * [VanishSync] propagates toggles across the network so vanish is consistent on every server. Players
 * hidden by *other* rules keep normal physics and messaging — only vanished players become ghosts.
 */
class VisibilityModule : PaperModule() {

    override val id = "visibility"

    private lateinit var visibility: VisibilityManager
    private lateinit var vanish: Vanish
    private val subscriptions = mutableListOf<Subscription>()
    private var store: VanishStore? = null

    override fun onLoad(context: ModuleContext) {
        super.onLoad(context) // captures the PaperModuleContext — keep first
        visibility = VisibilityManager(plugin)
        vanish = Vanish(visibility, plugin)
        visibility.addRule(vanish.rule)
        services.register(VisibilityService::class, visibility)
        registerCommands(VanishCommands(vanish, services.get(MessageService::class)))
        logger.info("Visibility loaded")
    }

    override fun onEnable() {
        subscriptions += Events.subscribe(PlayerJoinEvent::class.java).handler(::onJoin)
        subscriptions += Events.subscribe(PlayerQuitEvent::class.java).handler(::onQuit)

        // Vanish ghost physics — a vanished player is forgotten by mobs and picks nothing up.
        subscriptions += Events.subscribe(EntityTargetLivingEntityEvent::class.java)
            .filter { it.target is Player }
            .filter { vanish.isVanished(it.target as Player) }
            .handler {
                it.target = null
                it.isCancelled = true
            }

        subscriptions += Events.subscribe(EntityPickupItemEvent::class.java)
            .filter { it.entity is Player }
            .filter { vanish.isVanished(it.entity as Player) }
            .handler { it.isCancelled = true }

        // Can't /msg someone you can't see — answer as if they're offline so vanish isn't revealed.
        subscriptions += Events.subscribe(PlayerCommandPreprocessEvent::class.java).handler(::onCommand)

        // Durable + cross-server vanish, using only the infra that's actually connected (find, not get).
        val messenger = services.find(Messenger::class)
        val database = services.find(Database::class)
        if (messenger != null || database != null) {
            store = VanishStore(vanish, messenger, database, logger).also { it.start() }
            logger.info("Vanish backend started (redis={}, sql={})", messenger != null, database != null)
        } else {
            logger.info("No Redis/SQL — vanish is per-server and clears on restart")
        }

        visibility.refreshAll()
    }

    override fun onDisable() {
        super.onDisable()
        store?.stop()
        store = null
        subscriptions.forEach(Subscription::unregister)
        subscriptions.clear()
        visibility.revealAll() // never leave anyone hidden after the feature stops
    }

    private fun onJoin(event: PlayerJoinEvent) {
        if (vanish.isVanished(event.player)) event.joinMessage(null) // arrive silently (e.g. server switch)
        visibility.refresh(event.player)
    }

    private fun onQuit(event: PlayerQuitEvent) {
        if (vanish.isVanished(event.player)) event.quitMessage(null) // leave silently
    }

    private fun onCommand(event: PlayerCommandPreprocessEvent) {
        val parts = event.message.removePrefix("/").split(' ', limit = 3)
        if (parts.size < 2) return
        if (parts[0].substringAfterLast(':').lowercase() !in MESSAGE_COMMANDS) return
        val target = plugin.server.getPlayerExact(parts[1]) ?: return
        val sender = event.player
        if (sender.uniqueId != target.uniqueId && vanish.rule.hides(sender, target)) {
            event.isCancelled = true
            sender.sendMessage(CommonMessages.notOnline(parts[1], sender.resolvedLocale()))
        }
    }

    private companion object {
        private val MESSAGE_COMMANDS =
            setOf("msg", "tell", "w", "whisper", "message", "pm", "dm", "m", "t")
    }
}

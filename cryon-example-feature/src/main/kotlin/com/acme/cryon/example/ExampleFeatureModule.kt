package com.acme.cryon.example

import com.tricrotism.cryon.common.extension.formatBalance
import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.module.ModuleContext
import com.tricrotism.cryon.common.number.CryonNumber
import com.tricrotism.cryon.paper.api.PaperModule
import com.tricrotism.cryon.paper.api.event.Events
import com.tricrotism.cryon.paper.api.event.Subscription
import com.tricrotism.cryon.paper.api.extension.render
import com.tricrotism.cryon.paper.api.extension.send
import com.tricrotism.cryon.paper.api.item.ItemBuilder
import com.tricrotism.cryon.paper.api.scheduler.Schedulers
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Example feature, built in its own repo and loaded from a jar by the Cryon core. Declared in
 * `META-INF/services/com.tricrotism.cryon.common.module.Module`.
 *
 * It exercises the shared utilities end to end:
 *  - **i18n**: registers a `lang/en_US.properties` bundle with the shared [MessageService] and sends
 *    welcomes in the player's locale.
 *  - **Events**: a functional [PlayerJoinEvent] subscription, unregistered on disable.
 *  - **Schedulers**: an async heartbeat that logs aggregate visits (Folia-safe — no Bukkit API).
 *  - **CryonNumber**: tracks visits and shows `pow` + suffix formatting on a value that scales fast.
 *  - **ItemBuilder**: hands out a localized, glowing token.
 */
class ExampleFeatureModule : PaperModule() {

    override val id = "example"

    private val joins = ConcurrentHashMap<UUID, Int>()
    private lateinit var messages: MessageService
    private var joinSub: Subscription? = null
    private var heartbeat: ScheduledTask? = null

    override fun onLoad(context: ModuleContext) {
        super.onLoad(context) // captures the PaperModuleContext — keep this first
        messages = services.get(MessageService::class)
        // No addSource needed — the core auto-scans this jar's lang/en_US.properties on load.
        logger.info("Example feature loaded")
    }

    override fun onEnable() {
        joinSub = Events.subscribe(PlayerJoinEvent::class.java).handler(::onJoin)

        // Demo: Folia-safe async heartbeat summing visits through the scaling number type.
        heartbeat = Schedulers.asyncTimer(5, 5, TimeUnit.MINUTES) { _ ->
            val total = CryonNumber.of(joins.values.sumOf(Int::toLong))
            logger.info("Example: {} total visits tracked", total.formatBalance())
        }
    }

    override fun onDisable() {
        super.onDisable() // tears down any PaperModule.listen() listeners
        joinSub?.unregister()
        heartbeat?.cancel()
    }

    private fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val visits = joins.merge(player.uniqueId, 1) { a, b -> a + b }!!

        messages.send(
            player, "example.welcome",
            Placeholder.unparsed("player", player.name),
            Placeholder.unparsed("count", visits.toString()),
        )

        val token = ItemBuilder(Material.SUNFLOWER)
            .name(messages.render(player, "example.item_name"))
            .lore(messages.render(player, "example.item_lore", Placeholder.unparsed("visits", visits.toString())))
            .glow()
            .build()
        player.inventory.addItem(token)
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)

        // Showcase CryonNumber: 2^visits formats with suffixes even once it's astronomically large.
        logger.info("Example: {} joined; 2^{} = {}", player.name, visits, CryonNumber.of(2.0).pow(visits.toDouble()).formatBalance())
    }
}

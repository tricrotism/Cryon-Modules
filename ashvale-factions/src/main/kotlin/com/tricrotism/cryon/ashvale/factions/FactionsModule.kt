package com.tricrotism.cryon.ashvale.factions

import com.tricrotism.cryon.ashvale.factions.api.FactionService
import com.tricrotism.cryon.common.flag.FeatureFlags
import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.module.ModuleContext
import com.tricrotism.cryon.paper.api.PaperModule
import com.tricrotism.cryon.paper.api.event.Events
import com.tricrotism.cryon.paper.api.event.Subscription
import com.tricrotism.cryon.paper.api.scheduler.Schedulers
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.concurrent.TimeUnit

/**
 * The Ashvale factions core (Phase 1 foundation, `13`). Publishes [FactionService] so every other
 * Ashvale system (cannoning claim checks, the economy's `/f top`, raid rules) builds on the same
 * membership, claim, and power state.
 *
 * The vertical slice implemented here: `/f create`, power-gated `/f claim`, and player power that
 * bleeds on death and regenerates over time. Each independently-killable slice has its own flag.
 */
class FactionsModule : PaperModule() {

    override val id = "factions"

    private lateinit var manager: FactionManager
    private lateinit var flags: FeatureFlags
    private val subscriptions = mutableListOf<Subscription>()
    private var autosave: ScheduledTask? = null
    private var regen: ScheduledTask? = null

    override fun onLoad(context: ModuleContext) {
        super.onLoad(context) // captures the PaperModuleContext; keep first
        manager = FactionManager(plugin, logger)
        manager.load()
        services.register(FactionService::class, manager)
        flags = services.get(FeatureFlags::class)
        registerCommands(FactionCommands(manager, flags, services.get(MessageService::class)))
        logger.info("Ashvale factions loaded ({} factions)", manager.factionCount())
    }

    override fun onEnable() {
        flags.register(FLAG_CREATE)
        flags.register(FLAG_CLAIM)
        flags.register(FLAG_UNCLAIM)
        flags.register(FLAG_POWER_LOSS)
        flags.register(FLAG_PROTECTION)

        // Claim protection: non-members can't break/place/open on another faction's land.
        subscriptions += ClaimProtection(manager, flags, services.get(MessageService::class)).subscribe()

        // Power bleeds on death (silent path → isEnabled with the player, so a per-player override
        // applies). Only faction members carry power, so factionless deaths cost nothing.
        subscriptions += Events.subscribe(PlayerDeathEvent::class.java)
            .handler { event ->
                val uuid = event.entity.uniqueId
                if (!flags.isEnabled(FLAG_POWER_LOSS, uuid)) return@handler
                if (manager.factionOf(uuid) == null) return@handler
                manager.adjustPower(uuid, -DEATH_PENALTY)
            }

        // Regen and autosave touch only the manager's thread-safe maps (no Bukkit API), so async.
        regen = Schedulers.asyncTimer(REGEN_INTERVAL_SECONDS, REGEN_INTERVAL_SECONDS, TimeUnit.SECONDS) {
            manager.regenerateAll(POWER_REGEN)
        }
        autosave = Schedulers.asyncTimer(SAVE_INTERVAL_SECONDS, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS) {
            manager.save()
        }
    }

    override fun onDisable() {
        super.onDisable()
        subscriptions.forEach(Subscription::unregister)
        subscriptions.clear()
        regen?.cancel()
        regen = null
        autosave?.cancel()
        autosave = null
        manager.save() // flush anything the timer hasn't written yet
    }

    companion object {
        /** Kill switches, one per independently-meaningful slice. */
        const val FLAG_CREATE = "FACTIONS_CREATE"
        const val FLAG_CLAIM = "FACTIONS_CLAIM"
        const val FLAG_UNCLAIM = "FACTIONS_UNCLAIM"
        const val FLAG_POWER_LOSS = "FACTIONS_POWER_LOSS"
        const val FLAG_PROTECTION = "FACTIONS_PROTECTION"

        private const val DEATH_PENALTY = 2
        private const val POWER_REGEN = 1
        private const val REGEN_INTERVAL_SECONDS = 60L
        private const val SAVE_INTERVAL_SECONDS = 60L
    }
}

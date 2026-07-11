package com.tricrotism.cryon.economy

import com.tricrotism.cryon.common.flag.FeatureFlags
import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.module.ModuleContext
import com.tricrotism.cryon.paper.api.PaperModule
import com.tricrotism.cryon.paper.api.scheduler.Schedulers
import com.tricrotism.cryon.survival.api.EconomyService
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit

/**
 * The survival gamemode's currency: **Shards**. Publishes [EconomyService] (from the
 * `cryon-survival-api` contract jar) so any feature repo can pay or charge players — the shop
 * deposits sell proceeds, skills pays level rewards.
 *
 * Balances are [com.tricrotism.cryon.common.number.PackedDecimal] end to end, so the economy keeps
 * working (and formatting) long past `Long`/`Double` scale. Persistence is batched: mutations mark a
 * dirty bit and an async timer flushes to `plugins/Cryon/economy/balances.yml` — no per-event IO.
 */
class EconomyModule : PaperModule() {

    override val id = "economy"

    private lateinit var manager: EconomyManager
    private lateinit var flags: FeatureFlags
    private var autosave: ScheduledTask? = null
    private var leaderboard: ScheduledTask? = null

    override fun onLoad(context: ModuleContext) {
        super.onLoad(context) // captures the PaperModuleContext — keep first
        manager = EconomyManager(plugin, logger)
        manager.load()
        services.register(EconomyService::class, manager)
        flags = services.get(FeatureFlags::class)
        val messages = services.get(MessageService::class)
        registerCommands(
            BalanceCommands(manager, messages),
            PayCommands(manager, messages, flags),
            BaltopCommands(manager, messages, flags),
            EcoCommands(manager, messages),
        )
        logger.info("Economy loaded ({} balances)", manager.balanceCount())
    }

    override fun onEnable() {
        flags.register(FLAG_PAY)
        flags.register(FLAG_BALTOP)
        autosave = Schedulers.asyncTimer(SAVE_INTERVAL_SECONDS, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS) {
            manager.save()
        }
        // Precompute the /baltop leaderboard (full sort + offline-name resolution) off the main thread
        // so the command itself renders a cached snapshot instead of stalling the server on each run.
        leaderboard = Schedulers.asyncTimer(1, LEADERBOARD_REFRESH_SECONDS, TimeUnit.SECONDS) {
            manager.refreshLeaderboard(LEADERBOARD_SIZE)
        }
    }

    override fun onDisable() {
        super.onDisable()
        autosave?.cancel()
        autosave = null
        leaderboard?.cancel()
        leaderboard = null
        manager.save() // flush anything the timer hasn't written yet
    }

    companion object {
        /** Kill switches: player-to-player transfers, and the leaderboard. */
        const val FLAG_PAY = "ECONOMY_PAY"
        const val FLAG_BALTOP = "ECONOMY_BALTOP"

        private const val SAVE_INTERVAL_SECONDS = 60L

        /** `/baltop` size and how often its cached snapshot is recomputed off-thread. */
        private const val LEADERBOARD_SIZE = 10
        private const val LEADERBOARD_REFRESH_SECONDS = 60L
    }
}

package com.tricrotism.cryon.skills

import com.tricrotism.cryon.common.extension.formatBalance
import com.tricrotism.cryon.common.flag.FeatureFlags
import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.module.ModuleContext
import com.tricrotism.cryon.common.number.PackedDecimal
import com.tricrotism.cryon.paper.api.PaperModule
import com.tricrotism.cryon.paper.api.event.Events
import com.tricrotism.cryon.paper.api.event.Subscription
import com.tricrotism.cryon.paper.api.extension.render
import com.tricrotism.cryon.paper.api.extension.send
import com.tricrotism.cryon.paper.api.scheduler.Schedulers
import com.tricrotism.cryon.survival.api.*
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.block.data.Ageable
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.world.ChunkUnloadEvent
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Skill progression for the survival gamemode: five [Skill]s levelled by playing — mining ores,
 * felling logs, harvesting mature crops, catching fish, and killing mobs. Publishes [SkillsService]
 * (from `cryon-survival-api`) so other repos can award XP from their own mechanics.
 *
 * Levels feed back into the loop through two optional peers, both resolved with `find` and skipped
 * gracefully when absent: each level-up pays a Shards reward via [EconomyService], and total level
 * grants a sell-price boost registered into the shop's [SellService] modifier chain.
 *
 * Every XP source is its own kill switch (`SKILLS_MINING`, `SKILLS_FISHING`, …), checked inside the
 * handler so `/cryon flag` bites immediately; the reward and boost slices have their own flags too.
 * Ore/log/gourd XP is gated by a [PlacedBlockTracker] so place-and-rebreak can't farm it.
 */
class SkillsModule : PaperModule() {

    override val id = "skills"

    private lateinit var manager: SkillsManager
    private lateinit var flags: FeatureFlags
    private lateinit var messages: MessageService
    private val tracker = PlacedBlockTracker(XpTable.MINING.keys + XpTable.WOODCUTTING.keys + XpTable.GOURDS.keys)

    private var economy: EconomyService? = null
    private var sellService: SellService? = null
    private val subscriptions = mutableListOf<Subscription>()
    private var autosave: ScheduledTask? = null

    /** Total level → sell multiplier; a no-op 1.0 while the flag is off (for this player). */
    private val sellBoost = SellModifier { player, _ ->
        if (!flags.isEnabled(FLAG_SELL_BOOST, player.uniqueId)) 1.0
        else 1.0 + min(manager.totalLevel(player.uniqueId) * BOOST_PER_LEVEL, BOOST_CAP)
    }

    override fun onLoad(context: ModuleContext) {
        super.onLoad(context) // captures the PaperModuleContext — keep first
        manager = SkillsManager(plugin, logger)
        manager.load()
        services.register(SkillsService::class, manager)
        flags = services.get(FeatureFlags::class)
        messages = services.get(MessageService::class)
        registerCommands(SkillsCommands(manager, messages))
        logger.info("Skills loaded")
    }

    override fun onEnable() {
        listOf(
            FLAG_MINING, FLAG_WOODCUTTING, FLAG_FARMING, FLAG_FISHING, FLAG_COMBAT,
            FLAG_SELL_BOOST, FLAG_LEVEL_REWARD,
        ).forEach { flags.register(it) }
        economy = services.find(EconomyService::class)
        if (economy == null) logger.info("cryon-economy not present — level rewards disabled")
        manager.onLevelUp = ::celebrateLevelUp

        // The extension seam: boost sell prices by total skill level, if the shop is installed.
        val sell = services.find(SellService::class)
        sellService = sell
        if (sell == null) logger.info("cryon-shop not present — the skill sell boost is disabled")
        else sell.addModifier(sellBoost)

        subscriptions += Events.subscribe(BlockPlaceEvent::class.java, EventPriority.MONITOR)
            .ignoreCancelled()
            .handler { tracker.onPlace(it.block) }

        subscriptions += Events.subscribe(BlockBreakEvent::class.java, EventPriority.MONITOR)
            .ignoreCancelled()
            .handler(::onBlockBreak)

        subscriptions += Events.subscribe(PlayerFishEvent::class.java, EventPriority.MONITOR)
            .ignoreCancelled()
            .filter { it.state == PlayerFishEvent.State.CAUGHT_FISH }
            .handler { award(it.player, Skill.FISHING, FLAG_FISHING, XpTable.FISHING_XP) }

        subscriptions += Events.subscribe(EntityDeathEvent::class.java, EventPriority.MONITOR)
            .ignoreCancelled()
            .filter { it.entity !is Player } // PvP kills aren't a farmable XP source
            .handler { event ->
                val killer = event.entity.killer ?: return@handler
                award(killer, Skill.COMBAT, FLAG_COMBAT, XpTable.combatXp(event.entity))
            }

        subscriptions += Events.subscribe(ChunkUnloadEvent::class.java)
            .handler { tracker.evict(it.chunk) }

        autosave = Schedulers.asyncTimer(SAVE_INTERVAL_SECONDS, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS) {
            manager.save()
        }
    }

    override fun onDisable() {
        super.onDisable()
        subscriptions.forEach(Subscription::unregister)
        subscriptions.clear()
        autosave?.cancel()
        autosave = null
        manager.onLevelUp = null
        // Pull our modifier back out so a disabled skills module stops boosting sell prices.
        sellService?.removeModifier(sellBoost)
        sellService = null
        economy = null
        manager.save() // flush anything the timer hasn't written yet
    }

    private fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val type = block.type
        // Consume unconditionally (even with the skill flag off) so the tracker never goes stale.
        val placed = tracker.consume(block)

        XpTable.MINING[type]?.let { if (!placed) award(event.player, Skill.MINING, FLAG_MINING, it) }
        XpTable.WOODCUTTING[type]?.let { if (!placed) award(event.player, Skill.WOODCUTTING, FLAG_WOODCUTTING, it) }
        XpTable.GOURDS[type]?.let { if (!placed) award(event.player, Skill.FARMING, FLAG_FARMING, it) }
        XpTable.CROPS[type]?.let { xp ->
            val age = block.blockData as? Ageable ?: return@let
            if (age.age >= age.maximumAge) award(event.player, Skill.FARMING, FLAG_FARMING, xp)
        }
    }

    private fun award(player: Player, skill: Skill, flag: String, xp: Long) {
        if (!flags.isEnabled(flag, player.uniqueId)) return
        manager.addXp(player.uniqueId, skill, xp)
    }

    /** Level-up celebration + the (flagged, economy-backed) Shards reward. */
    private fun celebrateLevelUp(playerId: UUID, skill: Skill, newLevel: Int) {
        val player = server.getPlayer(playerId) ?: return
        messages.send(
            player, "skills.levelup",
            Placeholder.component("skill", messages.render(player, skillNameKey(skill))),
            Placeholder.unparsed("level", newLevel.toString()),
        )
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)

        val economy = economy ?: return
        if (!flags.isEnabled(FLAG_LEVEL_REWARD, playerId)) return
        val reward = PackedDecimal.of(REWARD_PER_LEVEL * newLevel)
        economy.deposit(playerId, reward)
        messages.send(
            player, "skills.levelup.reward",
            Placeholder.unparsed("amount", reward.formatBalance()),
            Placeholder.component("currency", messages.render(player, "economy.currency")),
        )
    }

    companion object {
        /** One kill switch per XP source, plus the two feedback slices into the economy/shop. */
        const val FLAG_MINING = "SKILLS_MINING"
        const val FLAG_WOODCUTTING = "SKILLS_WOODCUTTING"
        const val FLAG_FARMING = "SKILLS_FARMING"
        const val FLAG_FISHING = "SKILLS_FISHING"
        const val FLAG_COMBAT = "SKILLS_COMBAT"
        const val FLAG_SELL_BOOST = "SKILLS_SELL_BOOST"
        const val FLAG_LEVEL_REWARD = "SKILLS_LEVEL_REWARD"

        /** +0.5% sell value per total skill level, capped at +100%. */
        const val BOOST_PER_LEVEL = 0.005
        const val BOOST_CAP = 1.0

        private const val REWARD_PER_LEVEL = 25L
        private const val SAVE_INTERVAL_SECONDS = 60L

        private val SKILL_NAME_KEYS = Skill.entries.associateWith { "skills.name.${it.name.lowercase()}" }

        fun skillNameKey(skill: Skill): String = SKILL_NAME_KEYS.getValue(skill)
    }
}

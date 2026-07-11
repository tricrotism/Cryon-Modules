package com.tricrotism.cryon.economy

import com.tricrotism.cryon.common.number.PackedDecimal
import com.tricrotism.cryon.survival.api.BalanceEntry
import com.tricrotism.cryon.survival.api.EconomyService
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.slf4j.Logger
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The Shards ledger. Balances live in memory as [PackedDecimal]s and persist to
 * `plugins/Cryon/economy/balances.yml` — writes only mark a dirty bit, and [save] (driven by the
 * module's async autosave timer, plus once on disable) flushes the whole map in one batch, so
 * high-frequency earning never does per-event file IO.
 */
class EconomyManager(plugin: Plugin, private val logger: Logger) : EconomyService {

    private val balances = ConcurrentHashMap<UUID, PackedDecimal>()
    private val file = File(File(plugin.dataFolder, "economy"), "balances.yml")

    @Volatile
    private var dirty = false

    override fun balance(player: UUID): PackedDecimal = balances[player] ?: PackedDecimal.ZERO

    override fun deposit(player: UUID, amount: PackedDecimal): PackedDecimal {
        requirePositive(amount)
        val balance = balances.merge(player, amount) { a, b -> a + b }!!
        dirty = true
        return balance
    }

    override fun withdraw(player: UUID, amount: PackedDecimal): Boolean {
        requirePositive(amount)
        var success = false
        balances.computeIfPresent(player) { _, current ->
            if (current < amount) current
            else {
                success = true
                current - amount
            }
        }
        if (success) dirty = true
        return success
    }

    override fun transfer(from: UUID, to: UUID, amount: PackedDecimal): Boolean {
        if (!withdraw(from, amount)) return false
        deposit(to, amount)
        return true
    }

    override fun top(limit: Int): List<BalanceEntry> =
        balances.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { BalanceEntry(it.key, it.value) }

    @Volatile
    private var leaderboardSnapshot: List<LeaderboardEntry> = emptyList()

    /** The cached leaderboard — name-resolved and sorted; refreshed off-thread by [refreshLeaderboard]. */
    fun leaderboard(): List<LeaderboardEntry> = leaderboardSnapshot

    /**
     * Recompute the [leaderboard] snapshot: the top [limit] balances with display names resolved up
     * front. Safe off the main thread (a weakly consistent map read plus offline-player name lookups,
     * which are file IO like [save], touching no live server state) — so `/baltop` renders the result
     * with no sort and no per-row disk read on the command thread.
     */
    fun refreshLeaderboard(limit: Int) {
        leaderboardSnapshot = balances.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { LeaderboardEntry(Bukkit.getOfflinePlayer(it.key).name ?: "Unknown", it.value) }
    }

    /** Admin override — unlike [withdraw] this can set any non-negative value directly. */
    fun set(player: UUID, amount: PackedDecimal) {
        require(amount.signum() >= 0) { "balance must be non-negative" }
        if (amount.isZero) balances.remove(player) else balances[player] = amount
        dirty = true
    }

    fun balanceCount(): Int = balances.size

    fun load() {
        if (!file.isFile) return
        val section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("balances") ?: return
        for (key in section.getKeys(false)) {
            val id = runCatching { UUID.fromString(key) }.getOrNull()
            val value = section.getString(key)?.let { runCatching { PackedDecimal.parse(it) }.getOrNull() }
            if (id == null || value == null) {
                logger.warn("Skipping unreadable balance entry {}", key)
                continue
            }
            balances[id] = value
        }
    }

    /**
     * Flush to disk if anything changed since the last save. Safe off the main thread (a weakly
     * consistent snapshot of the map plus plain file IO); synchronized so the autosave timer and the
     * disable-time flush can't interleave writes.
     */
    @Synchronized
    fun save() {
        if (!dirty) return
        dirty = false
        val yaml = YamlConfiguration()
        for ((id, balance) in balances) yaml.set("balances.$id", balance.toScientificString())
        try {
            file.parentFile.mkdirs()
            yaml.save(file)
        } catch (e: Exception) {
            dirty = true // retry on the next flush
            logger.error("Failed to save balances to {}", file.path, e)
        }
    }

    private fun requirePositive(amount: PackedDecimal) =
        require(amount.signum() > 0) { "amount must be positive" }
}

/** A [EconomyManager.leaderboard] row with its display name pre-resolved, so `/baltop` does no IO. */
data class LeaderboardEntry(val name: String, val balance: PackedDecimal)

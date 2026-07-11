package com.tricrotism.cryon.survival.api

import com.tricrotism.cryon.common.number.PackedDecimal
import java.util.*

/**
 * The survival gamemode's currency (**Shards**), published into the Cryon `ServiceRegistry` by the
 * `cryon-economy` module. Balances are [PackedDecimal] so idle-scale values stay cheap and unbounded.
 *
 * Consumers resolve it in `onEnable` with `services.find(EconomyService::class)` (**find, not get** —
 * the economy is its own module and may be absent; degrade gracefully). All amounts must be positive;
 * that's the caller's invariant, validated before user input reaches this API.
 *
 * Thread-safe, but game-driven mutation normally happens on the main thread.
 */
interface EconomyService {

    /** [player]'s current balance (zero if they've never earned anything). */
    fun balance(player: UUID): PackedDecimal

    /** Add [amount] to [player]'s balance and return the new balance. */
    fun deposit(player: UUID, amount: PackedDecimal): PackedDecimal

    /** Remove [amount] from [player]'s balance. False (and no change) if they can't afford it. */
    fun withdraw(player: UUID, amount: PackedDecimal): Boolean

    /** Move [amount] from [from] to [to]. False (and no change) if [from] can't afford it. */
    fun transfer(from: UUID, to: UUID, amount: PackedDecimal): Boolean

    /** The [limit] richest players, descending. */
    fun top(limit: Int): List<BalanceEntry>
}

/** One `/baltop` row. */
data class BalanceEntry(val player: UUID, val balance: PackedDecimal)

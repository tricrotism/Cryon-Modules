package com.tricrotism.cryon.ashvale.factions.api

import java.util.*

/**
 * The Ashvale factions core, published into the Cryon `ServiceRegistry` by the `ashvale-factions`
 * module. It owns faction membership, chunk claims, and player power, the foundation every other
 * Ashvale system builds on (raiding checks claim ownership, the economy attributes value to a
 * faction, cannoning gates damage on the raid rules).
 *
 * Consumers in other repos resolve it in `onEnable` with `services.find(FactionService::class)`
 * (**find, not get**: factions may be absent; degrade gracefully). Mutating calls are main-thread;
 * queries are thread-safe.
 */
interface FactionService {

    /** Found a new faction led by [leader]. See [CreateResult] for the outcomes. */
    fun create(leader: UUID, name: String): CreateResult

    /** Claim [chunk] for [player]'s faction. See [ClaimResult] for the outcomes. */
    fun claim(player: UUID, chunk: ChunkKey): ClaimResult

    /** Release [chunk] from [player]'s faction. See [UnclaimResult] for the outcomes. */
    fun unclaim(player: UUID, chunk: ChunkKey): UnclaimResult

    /** The faction [player] belongs to, or `null` if they're factionless. */
    fun factionOf(player: UUID): Faction?

    /** The faction with this display name (case-insensitive), or `null`. */
    fun factionByName(name: String): Faction?

    /** The faction that owns [chunk], or `null` if it's unclaimed wilderness. */
    fun claimOwner(chunk: ChunkKey): Faction?

    /** [player]'s current individual power, whether or not they're in a faction. */
    fun powerOf(player: UUID): Int
}

/** The result of [FactionService.create]. */
sealed interface CreateResult {
    /** Founded: [faction] is the new, live faction with [leader] installed. */
    data class Success(val faction: Faction) : CreateResult

    /** The name failed validation (allowed: 3-16 of letters, digits, underscore). */
    data object InvalidName : CreateResult

    /** A live faction already uses this name (case-insensitive). */
    data class NameTaken(val name: String) : CreateResult

    /** The founder is already in a faction; leave it first. */
    data object AlreadyInFaction : CreateResult
}

/** The result of [FactionService.claim]. */
sealed interface ClaimResult {
    /** Claimed: [chunk] now belongs to [faction]. */
    data class Success(val faction: Faction, val chunk: ChunkKey) : ClaimResult

    /** The claimer isn't in a faction. */
    data object NotInFaction : ClaimResult

    /** The claimer's own faction already owns this chunk. */
    data object AlreadyOwned : ClaimResult

    /** Another faction ([owner]) already owns this chunk. */
    data class AlreadyClaimed(val owner: Faction) : ClaimResult

    /** The faction is at its land budget: it [have] power but the claim needs [need]. */
    data class NotEnoughPower(val have: Int, val need: Int) : ClaimResult
}

/** The result of [FactionService.unclaim]. */
sealed interface UnclaimResult {
    /** Released, [chunk] is wilderness again. */
    data class Success(val faction: Faction, val chunk: ChunkKey) : UnclaimResult

    /** The caller isn't in a faction. */
    data object NotInFaction : UnclaimResult

    /** The chunk is unclaimed wilderness. */
    data object NotClaimed : UnclaimResult

    /** The chunk belongs to another faction ([owner]), not the caller's. */
    data class NotYours(val owner: Faction) : UnclaimResult
}

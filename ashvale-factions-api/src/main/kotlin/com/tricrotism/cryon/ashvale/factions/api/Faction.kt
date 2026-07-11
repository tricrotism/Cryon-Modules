package com.tricrotism.cryon.ashvale.factions.api

import java.util.*

/**
 * A live, read-only view of one faction, published through the Cryon `ServiceRegistry` by the
 * `ashvale-factions` module. Properties reflect the faction's *current* state; [power] and [claims]
 * change over the faction's lifetime, so read them when you need them rather than caching a snapshot.
 *
 * Thread-safe to read; faction mutation happens on the main thread via [FactionService].
 */
interface Faction {

    /** Stable identity, independent of [name] (which may be reused after a disband). */
    val id: FactionId

    /** The display name, unique case-insensitively across live factions. */
    val name: String

    /** The founding member; holds [FactionRole.LEADER] until leadership is transferred. */
    val leader: UUID

    /** Every member, the [leader] included. */
    val members: Set<UUID>

    /** Every chunk this faction has claimed. */
    val claims: Set<ChunkKey>

    /**
     * The faction's land budget: the sum of its members' individual power, floored at zero. A faction
     * can hold at most [power] claimed chunks, so a member dying (losing power) can push a faction
     * "over-claimed" until it regenerates.
     */
    val power: Int

    /** [player]'s role in this faction, or `null` if they aren't a member. */
    fun role(player: UUID): FactionRole?
}

/** A member's authority within a faction. Higher ordinal = more authority. */
enum class FactionRole { MEMBER, OFFICER, LEADER }

/**
 * A faction's stable identity: a wrapper over a [UUID] so a faction id can never be confused with a
 * player id at a call site. Zero-allocation as a value class outside generic collections.
 */
@JvmInline
value class FactionId(val value: UUID)

/**
 * A single claimed chunk: a world plus chunk coordinates (block coordinate `>> 4`). This is what
 * cross-module consumers (cannon claim checks, raid rules) key protection on, so it lives in the
 * shared contract rather than any one module.
 */
data class ChunkKey(val world: UUID, val x: Int, val z: Int)

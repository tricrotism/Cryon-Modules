package com.tricrotism.cryon.ashvale.factions

import com.tricrotism.cryon.ashvale.factions.FactionManager.Companion.MAX_POWER
import com.tricrotism.cryon.ashvale.factions.FactionManager.Companion.MIN_POWER
import com.tricrotism.cryon.ashvale.factions.api.*
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.slf4j.Logger
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The Ashvale factions core: membership, chunk claims, and player power, all in memory with a batched
 * flush to `plugins/Cryon/ashvale-factions/factions.yml` (mutations mark a dirty bit; the module's
 * async timer and the disable hook write the whole state in one pass, so combat/regen never does
 * per-event IO, the same pattern as `cryon-economy`).
 *
 * Single-node persistence for the MVP (`13` Phase 1). The design's cross-node topology (`09`) will
 * move this behind the core `Database`/`RedisStore` so the war and crystal nodes share faction state;
 * that is the documented next step, and the `FactionService` contract is deliberately storage-blind so
 * the swap doesn't touch consumers.
 */
class FactionManager(plugin: Plugin, private val logger: Logger) : FactionService {

    private val factions = ConcurrentHashMap<FactionId, FactionData>()
    private val byName = ConcurrentHashMap<String, FactionId>() // lowercased name -> id
    private val membership = ConcurrentHashMap<UUID, FactionId>()
    private val claims = ConcurrentHashMap<ChunkKey, FactionId>()
    private val power = ConcurrentHashMap<UUID, Int>()

    private val file = File(File(plugin.dataFolder, "ashvale-factions"), "factions.yml")

    @Volatile
    private var dirty = false

    // --- FactionService (queries) ---

    override fun factionOf(player: UUID): Faction? = membership[player]?.let { factions[it] }

    override fun factionByName(name: String): Faction? = byName[name.lowercase()]?.let { factions[it] }

    override fun claimOwner(chunk: ChunkKey): Faction? = claims[chunk]?.let { factions[it] }

    override fun powerOf(player: UUID): Int = power[player] ?: DEFAULT_POWER

    // --- FactionService (mutations) ---

    override fun create(leader: UUID, name: String): CreateResult {
        if (!NAME_PATTERN.matches(name)) return CreateResult.InvalidName
        if (membership.containsKey(leader)) return CreateResult.AlreadyInFaction
        val key = name.lowercase()
        if (byName.containsKey(key)) return CreateResult.NameTaken(name)

        val faction = FactionData(FactionId(UUID.randomUUID()), name, leader, ::powerOf)
        factions[faction.id] = faction
        byName[key] = faction.id
        membership[leader] = faction.id
        power.putIfAbsent(leader, DEFAULT_POWER)
        dirty = true
        return CreateResult.Success(faction)
    }

    override fun claim(player: UUID, chunk: ChunkKey): ClaimResult {
        val id = membership[player] ?: return ClaimResult.NotInFaction
        val faction = factions[id] ?: return ClaimResult.NotInFaction

        val ownerId = claims[chunk]
        if (ownerId == id) return ClaimResult.AlreadyOwned
        ownerId?.let { factions[it] }?.let { return ClaimResult.AlreadyClaimed(it) }

        val budget = faction.power
        val needed = faction.claims.size + 1
        if (needed > budget) return ClaimResult.NotEnoughPower(budget, needed)

        claims[chunk] = id
        faction.claimSet.add(chunk)
        dirty = true
        return ClaimResult.Success(faction, chunk)
    }

    override fun unclaim(player: UUID, chunk: ChunkKey): UnclaimResult {
        val id = membership[player] ?: return UnclaimResult.NotInFaction
        val ownerId = claims[chunk] ?: return UnclaimResult.NotClaimed
        if (ownerId != id) {
            val owner = factions[ownerId] ?: return UnclaimResult.NotClaimed
            return UnclaimResult.NotYours(owner)
        }
        val faction = factions[id] ?: return UnclaimResult.NotInFaction
        claims.remove(chunk)
        faction.claimSet.remove(chunk)
        dirty = true
        return UnclaimResult.Success(faction, chunk)
    }

    // --- Power (module-internal; driven by the death listener and regen timer) ---

    /** Clamp-adjust [player]'s power by [delta] (bounded to [[MIN_POWER], [MAX_POWER]]). */
    fun adjustPower(player: UUID, delta: Int) {
        power.compute(player) { _, current -> ((current ?: DEFAULT_POWER) + delta).coerceIn(MIN_POWER, MAX_POWER) }
        dirty = true
    }

    /** Regenerate every faction member's power toward [MAX_POWER] by [amount]. */
    fun regenerateAll(amount: Int) {
        var changed = false
        for (player in membership.keys) {
            power.compute(player) { _, current ->
                val value = current ?: DEFAULT_POWER
                if (value >= MAX_POWER) value else {
                    changed = true; (value + amount).coerceAtMost(MAX_POWER)
                }
            }
        }
        if (changed) dirty = true
    }

    fun factionCount(): Int = factions.size

    // --- Persistence ---

    fun load() {
        if (!file.isFile) return
        val config = YamlConfiguration.loadConfiguration(file)
        config.getConfigurationSection("factions")?.let { section ->
            for (key in section.getKeys(false)) {
                runCatching { loadFaction(section, key) }
                    .onFailure { logger.warn("Skipping unreadable faction {}", key, it) }
            }
        }
        config.getConfigurationSection("power")?.let { section ->
            for (key in section.getKeys(false)) {
                val id = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                power[id] = section.getInt(key)
            }
        }
    }

    private fun loadFaction(section: org.bukkit.configuration.ConfigurationSection, key: String) {
        val id = FactionId(UUID.fromString(key))
        val name = section.getString("$key.name") ?: return
        val leader = UUID.fromString(section.getString("$key.leader"))
        val faction = FactionData(id, name, leader, ::powerOf)

        section.getConfigurationSection("$key.members")?.let { members ->
            for (memberKey in members.getKeys(false)) {
                val member = UUID.fromString(memberKey)
                faction.memberSet.add(member)
                faction.roles[member] = runCatching { FactionRole.valueOf(members.getString(memberKey)!!) }
                    .getOrDefault(FactionRole.MEMBER)
                membership[member] = id
            }
        }
        for (raw in section.getStringList("$key.claims")) {
            val chunk = parseChunk(raw) ?: continue
            faction.claimSet.add(chunk)
            claims[chunk] = id
        }
        factions[id] = faction
        byName[name.lowercase()] = id
    }

    /**
     * Flush to disk if anything changed. Safe off the main thread (a weakly consistent read of the
     * maps plus plain file IO); synchronized so the autosave timer and the disable-time flush can't
     * interleave writes.
     */
    @Synchronized
    fun save() {
        if (!dirty) return
        dirty = false
        val yaml = YamlConfiguration()
        for ((id, faction) in factions) {
            val base = "factions.${id.value}"
            yaml.set("$base.name", faction.name)
            yaml.set("$base.leader", faction.leader.toString())
            for ((member, role) in faction.roles) yaml.set("$base.members.$member", role.name)
            yaml.set("$base.claims", faction.claimSet.map { "${it.world}:${it.x}:${it.z}" })
        }
        for ((player, value) in power) yaml.set("power.$player", value)
        try {
            file.parentFile.mkdirs()
            yaml.save(file)
        } catch (e: Exception) {
            dirty = true // retry on the next flush
            logger.error("Failed to save factions to {}", file.path, e)
        }
    }

    private fun parseChunk(raw: String): ChunkKey? {
        val parts = raw.split(':')
        if (parts.size != 3) return null
        return runCatching { ChunkKey(UUID.fromString(parts[0]), parts[1].toInt(), parts[2].toInt()) }.getOrNull()
    }

    companion object {
        /** Power a player starts with, and the ceiling regen restores them to. */
        const val DEFAULT_POWER = 10
        const val MAX_POWER = 10
        private const val MIN_POWER = -10

        private val NAME_PATTERN = Regex("^[A-Za-z0-9_]{3,16}$")
    }
}

/**
 * The mutable backing for one [Faction]. Power isn't stored here; it's a per-player attribute the
 * [FactionManager] owns, so [power] reads live values through [powerProvider], keeping a member's
 * power consistent whether they're viewed through their faction or on their own.
 */
private class FactionData(
    override val id: FactionId,
    override val name: String,
    override val leader: UUID,
    private val powerProvider: (UUID) -> Int,
) : Faction {

    val memberSet: MutableSet<UUID> = Collections.newSetFromMap(ConcurrentHashMap())
    val claimSet: MutableSet<ChunkKey> = Collections.newSetFromMap(ConcurrentHashMap())
    val roles = ConcurrentHashMap<UUID, FactionRole>()

    init {
        memberSet.add(leader)
        roles[leader] = FactionRole.LEADER
    }

    override val members: Set<UUID> get() = memberSet
    override val claims: Set<ChunkKey> get() = claimSet
    override val power: Int get() = memberSet.sumOf(powerProvider).coerceAtLeast(0)
    override fun role(player: UUID): FactionRole? = roles[player]
}

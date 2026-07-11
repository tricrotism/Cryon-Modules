package com.tricrotism.cryon.skills

import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.block.Block
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers player-placed blocks so re-placing an ore or log can't farm skill XP. In-memory only,
 * keyed world → chunk → packed block position, and evicted per chunk on unload to bound memory —
 * which means a placed block "forgets" once its chunk unloads and reloads. That's the accepted
 * trade-off for not persisting per-block state; the XP values are small enough that the residual
 * farm isn't worth the bookkeeping.
 *
 * Only [tracked] materials (the ones that actually award XP) are recorded.
 */
class PlacedBlockTracker(private val tracked: Set<Material>) {

    private val worlds = ConcurrentHashMap<UUID, ConcurrentHashMap<Long, MutableSet<Long>>>()

    fun onPlace(block: Block) {
        if (block.type !in tracked) return
        worlds.computeIfAbsent(block.world.uid) { ConcurrentHashMap() }
            .computeIfAbsent(chunkKey(block)) { Collections.newSetFromMap(ConcurrentHashMap()) }
            .add(blockKey(block))
    }

    /** True (and untracked) if [block] was player-placed. Call on every break of a tracked material. */
    fun consume(block: Block): Boolean {
        val chunks = worlds[block.world.uid] ?: return false
        return chunks[chunkKey(block)]?.remove(blockKey(block)) ?: false
    }

    fun evict(chunk: Chunk) {
        worlds[chunk.world.uid]?.remove(chunkKey(chunk.x, chunk.z))
    }

    private fun chunkKey(block: Block): Long = chunkKey(block.x shr 4, block.z shr 4)

    private fun chunkKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    private fun blockKey(block: Block): Long =
        ((block.x.toLong() and 0x3FFFFFF) shl 38) or
                ((block.z.toLong() and 0x3FFFFFF) shl 12) or
                (block.y.toLong() and 0xFFF)
}

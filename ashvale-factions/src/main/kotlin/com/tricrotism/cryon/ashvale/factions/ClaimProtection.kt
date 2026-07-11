package com.tricrotism.cryon.ashvale.factions

import com.tricrotism.cryon.ashvale.factions.api.ChunkKey
import com.tricrotism.cryon.ashvale.factions.api.Faction
import com.tricrotism.cryon.ashvale.factions.api.FactionService
import com.tricrotism.cryon.common.flag.FeatureFlags
import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.text.MessageType
import com.tricrotism.cryon.paper.api.event.Events
import com.tricrotism.cryon.paper.api.event.Subscription
import com.tricrotism.cryon.paper.api.extension.send
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.data.Openable
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.InventoryHolder

/**
 * Makes claims mean something: a non-member can't break, place, or open containers/doors on land
 * another faction owns. Wilderness is unaffected, and so is `ashvale.factions.bypass` (staff). Gated
 * on [FactionsModule.FLAG_PROTECTION] per player, so a toggle bites without re-enabling the module.
 *
 * Explosions are deliberately *not* protected here: breaching a wall with TNT is the whole point of
 * Ashvale raiding (`06`), so cannon damage stays governed by the explosion engine, not this guard.
 */
class ClaimProtection(
    private val factions: FactionService,
    private val flags: FeatureFlags,
    private val messages: MessageService,
) {

    fun subscribe(): List<Subscription> = listOf(
        Events.subscribe(BlockBreakEvent::class.java).ignoreCancelled().handler { event ->
            enforce(event.player, chunkOf(event.block)) { event.isCancelled = true }
        },
        Events.subscribe(BlockPlaceEvent::class.java).ignoreCancelled().handler { event ->
            enforce(event.player, chunkOf(event.block)) { event.isCancelled = true }
        },
        Events.subscribe(PlayerInteractEvent::class.java).ignoreCancelled().handler { event ->
            if (event.action != Action.RIGHT_CLICK_BLOCK) return@handler
            val block = event.clickedBlock ?: return@handler
            if (!isProtectedInteraction(block)) return@handler
            enforce(event.player, chunkOf(block)) { event.setCancelled(true) }
        },
    )

    /** Cancel and warn if [player] may not act on [chunk]; a no-op on wilderness, own land, or bypass. */
    private inline fun enforce(player: Player, chunk: ChunkKey, cancel: () -> Unit) {
        val owner = blockingOwner(player, chunk) ?: return
        cancel()
        messages.send(player, MessageType.ERROR, "factions.protected", Placeholder.unparsed("faction", owner.name))
        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f)
    }

    /** The faction that owns [chunk] and bars [player], or null if the action is allowed. */
    private fun blockingOwner(player: Player, chunk: ChunkKey): Faction? {
        if (!flags.isEnabled(FactionsModule.FLAG_PROTECTION, player.uniqueId)) return null
        val owner = factions.claimOwner(chunk) ?: return null
        if (player.hasPermission(BYPASS_PERMISSION)) return null
        return if (factions.factionOf(player.uniqueId)?.id == owner.id) null else owner
    }

    private fun isProtectedInteraction(block: Block): Boolean =
        block.blockData is Openable || block.state is InventoryHolder

    private fun chunkOf(block: Block): ChunkKey = ChunkKey(block.world.uid, block.x shr 4, block.z shr 4)

    private companion object {
        const val BYPASS_PERMISSION = "ashvale.factions.bypass"
    }
}

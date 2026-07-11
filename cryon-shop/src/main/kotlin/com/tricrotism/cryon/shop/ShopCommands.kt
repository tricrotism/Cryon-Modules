package com.tricrotism.cryon.shop

import com.tricrotism.cryon.common.extension.formatBalance
import com.tricrotism.cryon.common.flag.FeatureFlags
import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.module.ServiceRegistry
import com.tricrotism.cryon.common.number.PackedDecimal
import com.tricrotism.cryon.common.text.CommonMessages
import com.tricrotism.cryon.common.text.MessageType
import com.tricrotism.cryon.paper.api.command.Command
import com.tricrotism.cryon.paper.api.command.Permission
import com.tricrotism.cryon.paper.api.command.Subcommand
import com.tricrotism.cryon.paper.api.extension.*
import com.tricrotism.cryon.survival.api.EconomyService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/sell hand | all` — sell the held stack, or everything sellable in the inventory. Proceeds are
 * deposited through [EconomyService], resolved from the registry **per dispatch** so a hot-swapped
 * economy is picked up (and its absence answered gracefully) without re-wiring. Both paths sit
 * behind the `SHOP_SELL` kill switch.
 */
@Command("sell", "Sell items for Shards")
@Permission("cryon.shop.use")
class SellCommands(
    private val shop: ShopManager,
    private val messages: MessageService,
    private val flags: FeatureFlags,
    private val services: ServiceRegistry,
) {

    @Subcommand
    fun usage(sender: CommandSender) {
        val player = requirePlayer(messages, sender) ?: return
        messages.send(player, MessageType.INFO, "shop.usage")
    }

    @Subcommand("hand")
    fun hand(sender: CommandSender) {
        val player = requirePlayer(messages, sender) ?: return
        if (!flags.guard(player, ShopModule.FLAG_SELL)) return
        val item = player.inventory.itemInMainHand
        if (item.isEmpty()) {
            messages.send(player, MessageType.ERROR, "shop.empty_hand")
            player.denySound()
            return
        }
        val value = shop.sellValue(player, item.type, item.amount)
        if (value == null) {
            messages.send(
                player, MessageType.ERROR, "shop.cannot_sell",
                Placeholder.component("item", Component.translatable(item.type)),
            )
            player.denySound()
            return
        }
        val economy = economyOrAck(player) ?: return
        player.inventory.setItemInMainHand(null)
        economy.deposit(player.uniqueId, value)
        ackSold(player, item.amount, value)
    }

    @Subcommand("all")
    fun all(sender: CommandSender) {
        val player = requirePlayer(messages, sender) ?: return
        if (!flags.guard(player, ShopModule.FLAG_SELL)) return
        val contents = player.inventory.storageContents
        var total = PackedDecimal.ZERO
        var count = 0
        val slots = ArrayList<Int>()
        for ((slot, stack) in contents.withIndex()) {
            if (stack.isEmpty()) continue
            val value = shop.sellValue(player, stack!!.type, stack.amount) ?: continue
            total += value
            count += stack.amount
            slots.add(slot)
        }
        if (count == 0) {
            messages.send(player, MessageType.ERROR, "shop.nothing_to_sell")
            player.denySound()
            return
        }
        val economy = economyOrAck(player) ?: return
        slots.forEach { player.inventory.setItem(it, null) }
        economy.deposit(player.uniqueId, total)
        ackSold(player, count, total)
    }

    /** The economy peer for this dispatch, or the localized "unavailable" ack. */
    private fun economyOrAck(player: Player): EconomyService? {
        val economy = services.find(EconomyService::class)
        if (economy == null) {
            messages.send(player, MessageType.ERROR, "shop.no_economy")
            player.denySound()
        }
        return economy
    }

    private fun ackSold(player: Player, count: Int, total: PackedDecimal) {
        player.sendMessage(
            CommonMessages.message(
                MessageType.SUCCESS,
                messages.renderPlural(
                    player.resolvedLocale(), "shop.sold", count.toLong(),
                    Placeholder.unparsed("count", count.toString()),
                    Placeholder.unparsed("amount", total.formatBalance()),
                    currency(messages, player),
                ),
            )
        )
        player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1f, 1.2f)
    }
}

/**
 * `/price` — what the held item currently sells for (per item and for the whole stack), with the
 * caller's modifiers applied. Behind the `SHOP_PRICE` kill switch.
 */
@Command("price", "Check what your held item sells for", "worth")
@Permission("cryon.shop.use")
class PriceCommands(
    private val shop: ShopManager,
    private val messages: MessageService,
    private val flags: FeatureFlags,
) {

    @Subcommand
    fun hand(sender: CommandSender) {
        val player = requirePlayer(messages, sender) ?: return
        if (!flags.guard(player, ShopModule.FLAG_PRICE)) return
        val item = player.inventory.itemInMainHand
        if (item.isEmpty()) {
            messages.send(player, MessageType.ERROR, "shop.empty_hand")
            player.denySound()
            return
        }
        val unit = shop.sellValue(player, item.type, 1)
        val total = shop.sellValue(player, item.type, item.amount)
        if (unit == null || total == null) {
            messages.send(
                player, MessageType.ERROR, "shop.cannot_sell",
                Placeholder.component("item", Component.translatable(item.type)),
            )
            player.denySound()
            return
        }
        messages.send(
            player, MessageType.INFO, "shop.price",
            Placeholder.component("item", Component.translatable(item.type)),
            Placeholder.unparsed("unit", unit.formatBalance()),
            Placeholder.unparsed("total", total.formatBalance()),
            currency(messages, player),
        )
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
    }
}

// Shared helpers for the command classes above.

private fun requirePlayer(messages: MessageService, sender: CommandSender): Player? {
    if (sender is Player) return sender
    sender.sendMessage(
        CommonMessages.error(messages.render(CommonMessages.defaultLocale, "shop.player_only"))
    )
    return null
}

/** The localized currency name (`<currency>`), styled by the economy lang bundle. */
private fun currency(messages: MessageService, player: Player): TagResolver =
    Placeholder.component("currency", messages.render(player, "economy.currency"))

private fun Player.denySound() = playSound(location, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f)

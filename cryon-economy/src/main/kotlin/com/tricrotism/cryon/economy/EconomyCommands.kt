package com.tricrotism.cryon.economy

import com.tricrotism.cryon.common.extension.formatBalance
import com.tricrotism.cryon.common.extension.parseBalance
import com.tricrotism.cryon.common.extension.pd
import com.tricrotism.cryon.common.flag.FeatureFlags
import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.number.PackedDecimal
import com.tricrotism.cryon.common.text.CommonMessages
import com.tricrotism.cryon.common.text.MessageType
import com.tricrotism.cryon.paper.api.command.Arg
import com.tricrotism.cryon.paper.api.command.Command
import com.tricrotism.cryon.paper.api.command.Permission
import com.tricrotism.cryon.paper.api.command.Subcommand
import com.tricrotism.cryon.paper.api.extension.*
import com.tricrotism.cryon.survival.api.EconomyService
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

/**
 * `/balance [player]` — check a balance; works from console too.
 */
@Command("balance", "Check a Shards balance", "bal")
@Permission("cryon.economy.use")
class BalanceCommands(private val economy: EconomyService, private val messages: MessageService) {

    @Subcommand
    fun own(sender: CommandSender) {
        val player = requirePlayer(messages, sender) ?: return
        messages.send(
            player, MessageType.INFO, "economy.balance",
            Placeholder.unparsed("amount", economy.balance(player.uniqueId).formatBalance()),
            currency(messages, player),
        )
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
    }

    @Subcommand
    fun other(sender: CommandSender, @Arg("player", suggests = "onlinePlayers") name: String) {
        val target = resolvePlayer(sender, name) ?: return
        feedback(
            messages, sender, MessageType.INFO, "economy.balance.other",
            Placeholder.unparsed("name", name),
            Placeholder.unparsed("amount", economy.balance(target).formatBalance()),
            currency(messages, sender),
        )
        (sender as? Player)?.playSound(sender.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
    }

    @Suppress("unused")
    fun onlinePlayers(): Collection<String> = onlinePlayerNames()
}

/**
 * `/pay <player> <amount>` — send Shards to an online player. Amounts accept balance shorthand
 * (`1.5k`, `2.35M`). The whole transfer path sits behind the `ECONOMY_PAY` kill switch.
 */
@Command("pay", "Send Shards to another player")
@Permission("cryon.economy.use")
class PayCommands(
    private val economy: EconomyService,
    private val messages: MessageService,
    private val flags: FeatureFlags,
) {

    @Subcommand
    fun pay(
        sender: CommandSender,
        @Arg("player", suggests = "onlinePlayers") name: String,
        @Arg("amount") amount: String,
    ) {
        val player = requirePlayer(messages, sender) ?: return
        if (!flags.guard(player, EconomyModule.FLAG_PAY)) return
        val target = Bukkit.getPlayerExact(name)
        if (target == null) {
            player.sendNotOnline(name)
            player.denySound()
            return
        }
        if (target.uniqueId == player.uniqueId) {
            messages.send(player, MessageType.ERROR, "economy.pay_self")
            player.denySound()
            return
        }
        val value = parseAmount(amount)
        if (value == null) {
            player.sendInvalidAmount(amount)
            player.denySound()
            return
        }
        if (!economy.transfer(player.uniqueId, target.uniqueId, value)) {
            player.sendNotEnough(messages.render(player, "economy.currency"))
            player.denySound()
            return
        }
        val formatted = value.formatBalance()
        messages.send(
            player, MessageType.SUCCESS, "economy.paid",
            Placeholder.unparsed("amount", formatted),
            Placeholder.unparsed("name", target.name),
            currency(messages, player),
        )
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
        messages.send(
            target, MessageType.SUCCESS, "economy.received",
            Placeholder.unparsed("amount", formatted),
            Placeholder.unparsed("name", player.name),
            currency(messages, target),
        )
        target.playSound(target.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
    }

    @Suppress("unused")
    fun onlinePlayers(): Collection<String> = onlinePlayerNames()
}

/**
 * `/baltop` — the ten richest players. Behind the `ECONOMY_BALTOP` kill switch.
 */
@Command("baltop", "The richest players", "balancetop")
@Permission("cryon.economy.use")
class BaltopCommands(
    private val economy: EconomyManager,
    private val messages: MessageService,
    private val flags: FeatureFlags,
) {

    @Subcommand
    fun top(sender: CommandSender) {
        if (!flags.isEnabled(EconomyModule.FLAG_BALTOP, (sender as? Player)?.uniqueId)) {
            sender.sendMessage(CommonMessages.featureDisabled(EconomyModule.FLAG_BALTOP, localeOf(sender)))
            return
        }
        val entries = economy.leaderboard()
        if (entries.isEmpty()) {
            feedback(messages, sender, MessageType.INFO, "economy.baltop.empty", currency(messages, sender))
            return
        }
        val locale = localeOf(sender)
        feedback(
            messages, sender, MessageType.INFO, "economy.baltop.header",
            Placeholder.unparsed("count", entries.size.toString()),
        )
        entries.forEachIndexed { index, entry ->
            sender.sendMessage(
                messages.render(
                    locale, "economy.baltop.entry",
                    Placeholder.unparsed("rank", (index + 1).toString()),
                    Placeholder.unparsed("name", entry.name),
                    Placeholder.unparsed("amount", entry.balance.formatBalance()),
                )
            )
        }
        (sender as? Player)?.playSound(sender.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
    }
}

/**
 * `/eco give|take|set <player> <amount>` — the admin ledger. Targets may be offline (any player the
 * server has seen); amounts accept balance shorthand.
 */
@Command("eco", "Economy admin")
@Permission("cryon.economy.admin")
class EcoCommands(private val economy: EconomyManager, private val messages: MessageService) {

    @Subcommand("give")
    fun give(
        sender: CommandSender,
        @Arg("player", suggests = "onlinePlayers") name: String,
        @Arg("amount") amount: String,
    ) {
        val target = resolvePlayer(sender, name) ?: return
        val value = parseAmount(amount) ?: return invalidAmount(sender, amount)
        economy.deposit(target, value)
        ack(sender, "economy.admin.gave", name, value)
    }

    @Subcommand("take")
    fun take(
        sender: CommandSender,
        @Arg("player", suggests = "onlinePlayers") name: String,
        @Arg("amount") amount: String,
    ) {
        val target = resolvePlayer(sender, name) ?: return
        val value = parseAmount(amount) ?: return invalidAmount(sender, amount)
        if (!economy.withdraw(target, value)) {
            feedback(
                messages, sender, MessageType.ERROR, "economy.admin.not_enough",
                Placeholder.unparsed("name", name),
            )
            return
        }
        ack(sender, "economy.admin.took", name, value)
    }

    @Subcommand("set")
    fun set(
        sender: CommandSender,
        @Arg("player", suggests = "onlinePlayers") name: String,
        @Arg("amount") amount: String,
    ) {
        val target = resolvePlayer(sender, name) ?: return
        val value = runCatching { amount.parseBalance().pd }.getOrNull()
        if (value == null || value.signum() < 0) return invalidAmount(sender, amount)
        economy.set(target, value)
        ack(sender, "economy.admin.set", name, value)
    }

    @Suppress("unused")
    fun onlinePlayers(): Collection<String> = onlinePlayerNames()

    private fun ack(sender: CommandSender, key: String, name: String, value: PackedDecimal) {
        feedback(
            messages, sender, MessageType.SUCCESS, key,
            Placeholder.unparsed("name", name),
            Placeholder.unparsed("amount", value.formatBalance()),
            currency(messages, sender),
        )
        (sender as? Player)?.playSound(sender.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
    }

    private fun invalidAmount(sender: CommandSender, raw: String) {
        if (sender is Player) sender.sendInvalidAmount(raw) else sender.sendMessage(CommonMessages.errorAmount(raw))
    }
}

// Shared helpers for the command classes above.

private fun localeOf(sender: CommandSender): Locale =
    (sender as? Player)?.resolvedLocale() ?: CommonMessages.defaultLocale

/** Prefixed, localized ack that also works for console senders. */
private fun feedback(
    messages: MessageService,
    sender: CommandSender,
    type: MessageType,
    key: String,
    vararg resolvers: TagResolver,
) {
    sender.sendMessage(CommonMessages.message(type, messages.render(localeOf(sender), key, *resolvers)))
}

/** The localized currency name (`<currency>`), styled by the economy lang bundle. */
private fun currency(messages: MessageService, sender: CommandSender): TagResolver =
    Placeholder.component("currency", messages.render(localeOf(sender), "economy.currency"))

private fun requirePlayer(messages: MessageService, sender: CommandSender): Player? {
    if (sender is Player) return sender
    feedback(messages, sender, MessageType.ERROR, "economy.player_only")
    return null
}

/** Resolve an online or previously-seen player, or ack the sender that they're unknown. */
private fun resolvePlayer(sender: CommandSender, name: String): UUID? {
    Bukkit.getPlayerExact(name)?.let { return it.uniqueId }
    Bukkit.getOfflinePlayerIfCached(name)?.let { return it.uniqueId }
    sender.sendMessage(CommonMessages.errorPlayer(name, localeOf(sender)))
    (sender as? Player)?.denySound()
    return null
}

/** Positive balance-shorthand amount (`250`, `1.5k`), or null if unparseable or non-positive. */
private fun parseAmount(raw: String): PackedDecimal? {
    val value = runCatching { raw.parseBalance().pd }.getOrNull() ?: return null
    return if (value.signum() > 0) value else null
}

private fun onlinePlayerNames(): Collection<String> = Bukkit.getOnlinePlayers().map { it.name }

private fun Player.denySound() = playSound(location, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f)

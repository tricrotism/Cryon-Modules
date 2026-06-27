package com.tricrotism.cryon.visibility.vanish

import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.text.CommonMessages
import com.tricrotism.cryon.common.text.MessageType
import com.tricrotism.cryon.paper.api.command.Arg
import com.tricrotism.cryon.paper.api.command.Command
import com.tricrotism.cryon.paper.api.command.Permission
import com.tricrotism.cryon.paper.api.command.Subcommand
import com.tricrotism.cryon.paper.api.extension.resolvedLocale
import com.tricrotism.cryon.paper.api.extension.send
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.Locale

/**
 * `/vanish [player] | list` — toggle vanish on yourself, on another player (`cryon.vanish.others`),
 * or list who is currently vanished (`cryon.vanish.see`). Annotation-defined, gated by
 * `cryon.vanish.use`. Acks are localized in the recipient's resolved locale and styled with a
 * [CommonMessages] prefix; every toggle plays a sound so the state change is never silent.
 */
@Command("vanish", "Toggle vanish — disappear from players without permission to see you", "v")
@Permission("cryon.vanish.use")
class VanishCommands(private val vanish: Vanish, private val messages: MessageService) {

    @Subcommand
    fun toggleSelf(sender: CommandSender) {
        val player = requirePlayer(sender) ?: return
        notify(player, vanish.toggle(player))
    }

    @Subcommand("list")
    @Permission(Vanish.PERM_SEE)
    fun list(sender: CommandSender) {
        val vanished = Bukkit.getOnlinePlayers().filter(vanish::isVanished)
        if (vanished.isEmpty()) {
            feedback(sender, MessageType.INFO, "vanish.list_empty")
            return
        }
        feedback(sender, MessageType.INFO, "vanish.list_header", Placeholder.unparsed("count", vanished.size.toString()))
        val locale = localeOf(sender)
        vanished.forEach {
            sender.sendMessage(messages.render(locale, "vanish.list_entry", Placeholder.unparsed("player", it.name)))
        }
    }

    @Subcommand
    @Permission("cryon.vanish.others")
    fun toggleOther(sender: CommandSender, @Arg("player", suggests = "players") name: String) {
        val target = Bukkit.getPlayerExact(name)
        if (target == null) {
            sender.sendMessage(CommonMessages.notOnline(name, localeOf(sender)))
            return
        }
        val now = vanish.toggle(target)
        notify(target, now)
        feedback(
            sender, MessageType.INFO,
            if (now) "vanish.enabled_other" else "vanish.disabled_other",
            Placeholder.unparsed("player", target.name),
        )
    }

    /** Suggester referenced by `@Arg(suggests = "players")`. */
    @Suppress("unused")
    fun players(): Collection<String> = Bukkit.getOnlinePlayers().map { it.name }

    private fun notify(player: Player, vanished: Boolean) {
        messages.send(player, if (vanished) MessageType.SUCCESS else MessageType.INFO, if (vanished) "vanish.enabled" else "vanish.disabled")
        player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, if (vanished) 0.8f else 1.5f)
    }

    private fun requirePlayer(sender: CommandSender): Player? {
        if (sender is Player) return sender
        feedback(sender, MessageType.ERROR, "vanish.player_only")
        return null
    }

    private fun feedback(sender: CommandSender, type: MessageType, key: String, vararg resolvers: TagResolver) {
        sender.sendMessage(CommonMessages.message(type, messages.render(localeOf(sender), key, *resolvers)))
    }

    private fun localeOf(sender: CommandSender): Locale =
        (sender as? Player)?.resolvedLocale() ?: CommonMessages.defaultLocale
}

package com.tricrotism.cryon.spawn

import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.text.CommonMessages
import com.tricrotism.cryon.common.text.MessageType
import com.tricrotism.cryon.paper.api.command.Command
import com.tricrotism.cryon.paper.api.command.Permission
import com.tricrotism.cryon.paper.api.command.Subcommand
import com.tricrotism.cryon.paper.api.extension.resolvedLocale
import com.tricrotism.cryon.paper.api.extension.send
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/spawn | spawn set` — teleport to spawn (`cryon.spawn.use`), or set it to your location
 * (`cryon.spawn.set`). Acks are localized and styled with a [CommonMessages] prefix, with a sound on
 * every action.
 */
@Command("spawn", "Teleport to spawn")
@Permission("cryon.spawn.use")
class SpawnCommands(private val spawn: Spawn, private val messages: MessageService) {

    @Subcommand
    fun teleport(sender: CommandSender) {
        val player = requirePlayer(sender) ?: return
        val target = spawn.location()
        if (target == null) {
            feedback(sender, MessageType.ERROR, "spawn.not_set")
            return
        }
        player.teleportAsync(target).thenAccept { moved ->
            if (moved) {
                messages.send(player, MessageType.SUCCESS, "spawn.teleported")
                player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f)
            }
        }
    }

    @Subcommand("set")
    @Permission("cryon.spawn.set")
    fun set(sender: CommandSender) {
        val player = requirePlayer(sender) ?: return
        spawn.set(player.location)
        messages.send(player, MessageType.SUCCESS, "spawn.set")
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
    }

    private fun requirePlayer(sender: CommandSender): Player? {
        if (sender is Player) return sender
        feedback(sender, MessageType.ERROR, "spawn.player_only")
        return null
    }

    private fun feedback(sender: CommandSender, type: MessageType, key: String, vararg resolvers: TagResolver) {
        val locale = (sender as? Player)?.resolvedLocale() ?: CommonMessages.defaultLocale
        sender.sendMessage(CommonMessages.message(type, messages.render(locale, key, *resolvers)))
    }
}

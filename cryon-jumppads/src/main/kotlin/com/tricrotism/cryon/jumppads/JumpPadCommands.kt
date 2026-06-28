package com.tricrotism.cryon.jumppads

import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.text.CommonMessages
import com.tricrotism.cryon.common.text.MessageType
import com.tricrotism.cryon.jumppads.api.JumpPadService
import com.tricrotism.cryon.paper.api.command.Command
import com.tricrotism.cryon.paper.api.command.Permission
import com.tricrotism.cryon.paper.api.command.Subcommand
import com.tricrotism.cryon.paper.api.extension.resolvedLocale
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/jumppad add | remove | list` — turn the block you're looking at into a jump pad, take it back, or
 * count them (`cryon.jumppads.admin`). "The block you're looking at" is a short ray-trace, falling back
 * to the block under your feet when you aren't aimed at anything. Acks are localized with a
 * [CommonMessages] prefix and every edit plays a sound.
 *
 * The mechanic itself needs no command — stepping on a pad launches you. This is just authoring.
 */
@Command("jumppad", "Create and manage jump pads", "jp")
@Permission("cryon.jumppads.admin")
class JumpPadCommands(private val pads: JumpPadService, private val messages: MessageService) {

    @Subcommand("add")
    fun add(sender: CommandSender) {
        val player = requirePlayer(sender) ?: return
        val block = targetBlock(player)
        if (block.type.isAir) {
            feedback(sender, MessageType.ERROR, "jumppads.no_block")
            return
        }

        if (!pads.addPad(block.location)) {
            feedback(sender, MessageType.INFO, "jumppads.already_pad")
            return
        }

        feedback(sender, MessageType.SUCCESS, "jumppads.added", *coords(block))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
    }

    @Subcommand("remove")
    fun remove(sender: CommandSender) {
        val player = requirePlayer(sender) ?: return
        val block = targetBlock(player)
        if (!pads.removePad(block.location)) {
            feedback(sender, MessageType.INFO, "jumppads.not_pad")
            return
        }

        feedback(sender, MessageType.SUCCESS, "jumppads.removed", *coords(block))
        player.playSound(player.location, Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f)
    }

    @Subcommand("list")
    fun list(sender: CommandSender) {
        feedback(sender, MessageType.INFO, "jumppads.list", Placeholder.unparsed("count", pads.padCount().toString()))
    }

    /** The block the player is aimed at (short ray-trace), or the block under their feet as a fallback. */
    private fun targetBlock(player: Player): Block =
        player.rayTraceBlocks(6.0)?.hitBlock ?: player.location.block.getRelative(BlockFace.DOWN)

    private fun coords(block: Block): Array<TagResolver> = arrayOf(
        Placeholder.unparsed("x", block.x.toString()),
        Placeholder.unparsed("y", block.y.toString()),
        Placeholder.unparsed("z", block.z.toString()),
    )

    private fun requirePlayer(sender: CommandSender): Player? {
        if (sender is Player) return sender
        feedback(sender, MessageType.ERROR, "jumppads.player_only")
        return null
    }

    private fun feedback(sender: CommandSender, type: MessageType, key: String, vararg resolvers: TagResolver) {
        val locale = (sender as? Player)?.resolvedLocale() ?: CommonMessages.defaultLocale
        sender.sendMessage(CommonMessages.message(type, messages.render(locale, key, *resolvers)))
    }
}

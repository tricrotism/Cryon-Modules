package com.tricrotism.cryon.ashvale.factions

import com.tricrotism.cryon.ashvale.factions.api.*
import com.tricrotism.cryon.common.flag.FeatureFlags
import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.text.CommonMessages
import com.tricrotism.cryon.common.text.MessageType
import com.tricrotism.cryon.paper.api.command.Arg
import com.tricrotism.cryon.paper.api.command.Command
import com.tricrotism.cryon.paper.api.command.Permission
import com.tricrotism.cryon.paper.api.command.Subcommand
import com.tricrotism.cryon.paper.api.extension.guard
import com.tricrotism.cryon.paper.api.extension.send
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/f`: the player-facing factions surface (`ashvale.factions.use`). `create` and `claim` sit behind
 * their own kill switches (checked *inside* the handler, so a runtime `/cryon flag` toggle bites); the
 * read-only `info` / `power` are always available while the module is enabled.
 */
@Command("f", "Ashvale factions", "faction", "factions")
@Permission("ashvale.factions.use")
class FactionCommands(
    private val factions: FactionService,
    private val flags: FeatureFlags,
    private val messages: MessageService,
) {

    @Subcommand
    fun overview(sender: CommandSender) {
        val player = requirePlayer(sender) ?: return
        messages.send(player, MessageType.INFO, "factions.help")
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f)
    }

    @Subcommand("create")
    fun create(sender: CommandSender, @Arg("name") name: String) {
        val player = requirePlayer(sender) ?: return
        if (!flags.guard(player, FactionsModule.FLAG_CREATE)) return
        when (val result = factions.create(player.uniqueId, name)) {
            is CreateResult.Success -> {
                messages.send(
                    player,
                    MessageType.SUCCESS,
                    "factions.create.success",
                    Placeholder.unparsed("name", result.faction.name)
                )
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
            }

            CreateResult.InvalidName -> deny(player, "factions.create.invalid_name")
            is CreateResult.NameTaken -> deny(
                player,
                "factions.create.name_taken",
                Placeholder.unparsed("name", result.name)
            )

            CreateResult.AlreadyInFaction -> deny(player, "factions.create.already_in_faction")
        }
    }

    @Subcommand("claim")
    fun claim(sender: CommandSender) {
        val player = requirePlayer(sender) ?: return
        if (!flags.guard(player, FactionsModule.FLAG_CLAIM)) return
        val chunk = currentChunk(player)
        when (val result = factions.claim(player.uniqueId, chunk)) {
            is ClaimResult.Success -> {
                messages.send(
                    player, MessageType.SUCCESS, "factions.claim.success",
                    Placeholder.unparsed("x", result.chunk.x.toString()),
                    Placeholder.unparsed("z", result.chunk.z.toString()),
                )
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f)
            }

            ClaimResult.NotInFaction -> deny(player, "factions.claim.not_in_faction")
            ClaimResult.AlreadyOwned -> deny(player, "factions.claim.already_self")
            is ClaimResult.AlreadyClaimed -> deny(
                player,
                "factions.claim.already_other",
                Placeholder.unparsed("faction", result.owner.name)
            )

            is ClaimResult.NotEnoughPower -> deny(
                player, "factions.claim.not_enough_power",
                Placeholder.unparsed("have", result.have.toString()),
                Placeholder.unparsed("need", result.need.toString()),
            )
        }
    }

    @Subcommand("unclaim")
    fun unclaim(sender: CommandSender) {
        val player = requirePlayer(sender) ?: return
        if (!flags.guard(player, FactionsModule.FLAG_UNCLAIM)) return
        val chunk = currentChunk(player)
        when (val result = factions.unclaim(player.uniqueId, chunk)) {
            is UnclaimResult.Success -> {
                messages.send(
                    player, MessageType.SUCCESS, "factions.unclaim.success",
                    Placeholder.unparsed("x", result.chunk.x.toString()),
                    Placeholder.unparsed("z", result.chunk.z.toString()),
                )
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f)
            }

            UnclaimResult.NotInFaction -> deny(player, "factions.claim.not_in_faction")
            UnclaimResult.NotClaimed -> deny(player, "factions.unclaim.not_claimed")
            is UnclaimResult.NotYours -> deny(
                player,
                "factions.unclaim.not_yours",
                Placeholder.unparsed("faction", result.owner.name)
            )
        }
    }

    @Subcommand("info")
    fun info(sender: CommandSender) {
        val player = requirePlayer(sender) ?: return
        val faction = factions.factionOf(player.uniqueId)
        if (faction == null) {
            deny(player, "factions.power.not_in_faction")
            return
        }
        showInfo(player, faction)
    }

    @Subcommand("info")
    fun infoOther(sender: CommandSender, @Arg("name") name: String) {
        val player = requirePlayer(sender) ?: return
        val faction = factions.factionByName(name)
        if (faction == null) {
            deny(player, "factions.info.unknown", Placeholder.unparsed("name", name))
            return
        }
        showInfo(player, faction)
    }

    @Subcommand("power")
    fun power(sender: CommandSender) {
        val player = requirePlayer(sender) ?: return
        if (factions.factionOf(player.uniqueId) == null) {
            deny(player, "factions.power.not_in_faction")
            return
        }
        messages.send(
            player, MessageType.INFO, "factions.power.self",
            Placeholder.unparsed("power", factions.powerOf(player.uniqueId).toString()),
            Placeholder.unparsed("max", FactionManager.MAX_POWER.toString()),
        )
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
    }

    private fun showInfo(player: Player, faction: Faction) {
        val leaderName = Bukkit.getOfflinePlayer(faction.leader).name ?: "Unknown"
        messages.send(player, MessageType.INFO, "factions.info.header", Placeholder.unparsed("name", faction.name))
        messages.send(player, MessageType.INFO, "factions.info.leader", Placeholder.unparsed("name", leaderName))
        messages.send(
            player,
            MessageType.INFO,
            "factions.info.members",
            Placeholder.unparsed("count", faction.members.size.toString())
        )
        messages.send(
            player,
            MessageType.INFO,
            "factions.info.power",
            Placeholder.unparsed("power", faction.power.toString())
        )
        messages.send(
            player,
            MessageType.INFO,
            "factions.info.claims",
            Placeholder.unparsed("count", faction.claims.size.toString())
        )
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f)
    }

    private fun currentChunk(player: Player): ChunkKey {
        val location = player.location
        return ChunkKey(location.world.uid, location.blockX shr 4, location.blockZ shr 4)
    }

    private fun requirePlayer(sender: CommandSender): Player? {
        if (sender is Player) return sender
        sender.sendMessage(
            CommonMessages.message(
                MessageType.ERROR,
                messages.render(CommonMessages.defaultLocale, "factions.player_only")
            )
        )
        return null
    }

    private fun deny(player: Player, key: String, vararg resolvers: TagResolver) {
        messages.send(player, MessageType.ERROR, key, *resolvers)
        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f)
    }
}

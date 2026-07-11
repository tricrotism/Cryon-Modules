package com.tricrotism.cryon.skills

import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.number.NumberUtils
import com.tricrotism.cryon.common.text.CommonMessages
import com.tricrotism.cryon.common.text.MessageType
import com.tricrotism.cryon.paper.api.command.Arg
import com.tricrotism.cryon.paper.api.command.Command
import com.tricrotism.cryon.paper.api.command.Permission
import com.tricrotism.cryon.paper.api.command.Subcommand
import com.tricrotism.cryon.paper.api.extension.resolvedLocale
import com.tricrotism.cryon.survival.api.Skill
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*
import kotlin.math.min

/**
 * `/skills [player]` — levels and XP progress per skill, plus the total level and the sell bonus it
 * grants. Works from console when a target is named.
 */
@Command("skills", "Your skill levels", "skill")
@Permission("cryon.skills.use")
class SkillsCommands(private val manager: SkillsManager, private val messages: MessageService) {

    @Subcommand
    fun own(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(
                CommonMessages.error(
                    messages.render(
                        CommonMessages.defaultLocale,
                        "skills.player_only"
                    )
                )
            )
            return
        }
        show(sender, player.uniqueId, player.name)
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
    }

    @Subcommand
    fun other(sender: CommandSender, @Arg("player", suggests = "onlinePlayers") name: String) {
        val locale = localeOf(sender)
        val target = Bukkit.getPlayerExact(name)?.uniqueId ?: Bukkit.getOfflinePlayerIfCached(name)?.uniqueId
        if (target == null) {
            sender.sendMessage(CommonMessages.errorPlayer(name, locale))
            return
        }
        show(sender, target, name)
        (sender as? Player)?.playSound(sender.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
    }

    @Suppress("unused")
    fun onlinePlayers(): Collection<String> = Bukkit.getOnlinePlayers().map { it.name }

    private fun show(sender: CommandSender, target: UUID, name: String) {
        val locale = localeOf(sender)
        val lines = ArrayList<Component>(Skill.entries.size + 2)
        lines += CommonMessages.message(
            MessageType.INFO,
            messages.render(locale, "skills.header", Placeholder.unparsed("name", name)),
        )
        for (skill in Skill.entries) {
            val progress = manager.progressOf(target, skill)
            lines += messages.render(
                locale, "skills.entry",
                Placeholder.component("skill", messages.render(locale, SkillsModule.skillNameKey(skill))),
                Placeholder.unparsed("level", progress.level.toString()),
                Placeholder.unparsed("xp", progress.xp.toString()),
                Placeholder.unparsed("needed", SkillsManager.xpForNext(progress.level).toString()),
            )
        }
        val total = manager.totalLevel(target)
        val bonus = min(total * SkillsModule.BOOST_PER_LEVEL, SkillsModule.BOOST_CAP) * 100
        lines += messages.render(
            locale, "skills.total",
            Placeholder.unparsed("level", total.toString()),
            Placeholder.unparsed("bonus", NumberUtils.formatCommas(bonus)),
        )
        // One component, one send: a single chat packet (and one flatten/serialize) instead of seven.
        sender.sendMessage(Component.join(JoinConfiguration.newlines(), lines))
    }

    private fun localeOf(sender: CommandSender): Locale =
        (sender as? Player)?.resolvedLocale() ?: CommonMessages.defaultLocale
}

package com.tricrotism.cryon.skills

import com.tricrotism.cryon.skills.SkillsManager.Companion.xpForNext
import com.tricrotism.cryon.survival.api.Skill
import com.tricrotism.cryon.survival.api.SkillsService
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.slf4j.Logger
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The progression engine. Per player and skill it holds a level plus XP *into* that level; the
 * quadratic [xpForNext] curve keeps early levels quick and late ones a grind. Persists to
 * `plugins/Cryon/skills/progress.yml` with the same dirty-bit + batched-flush scheme as the economy.
 *
 * [onLevelUp] fires once per level gained (a big XP award can level several times) — the module
 * points it at the celebration + reward path while enabled and clears it on disable, so peers can
 * keep calling [addXp] against a disabled module without side effects.
 */
class SkillsManager(plugin: Plugin, private val logger: Logger) : SkillsService {

    data class Progress(val level: Int, val xp: Long)

    private val progress = ConcurrentHashMap<UUID, ConcurrentHashMap<Skill, Progress>>()
    private val file = File(File(plugin.dataFolder, "skills"), "progress.yml")

    @Volatile
    private var dirty = false

    /** Called on the awarding thread (normally main) for each level gained: (player, skill, newLevel). */
    @Volatile
    var onLevelUp: ((UUID, Skill, Int) -> Unit)? = null

    override fun level(player: UUID, skill: Skill): Int = progressOf(player, skill).level

    override fun totalLevel(player: UUID): Int =
        progress[player]?.values?.sumOf { it.level } ?: 0

    fun progressOf(player: UUID, skill: Skill): Progress =
        progress[player]?.get(skill) ?: Progress(0, 0)

    override fun addXp(player: UUID, skill: Skill, amount: Long) {
        require(amount > 0) { "amount must be positive" }
        val levelsGained = ArrayList<Int>(1)
        progress.computeIfAbsent(player) { ConcurrentHashMap() }.compute(skill) { _, current ->
            var level = current?.level ?: 0
            var xp = (current?.xp ?: 0L) + amount
            while (xp >= xpForNext(level)) {
                xp -= xpForNext(level)
                level++
                levelsGained.add(level)
            }
            Progress(level, xp)
        }
        dirty = true
        val callback = onLevelUp ?: return
        levelsGained.forEach { callback(player, skill, it) }
    }

    fun load() {
        if (!file.isFile) return
        val root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("skills") ?: return
        for (key in root.getKeys(false)) {
            val id = runCatching { UUID.fromString(key) }.getOrNull()
            val section = root.getConfigurationSection(key)
            if (id == null || section == null) {
                logger.warn("Skipping unreadable skill entry {}", key)
                continue
            }
            val skills = ConcurrentHashMap<Skill, Progress>()
            for (skill in Skill.entries) {
                val name = skill.name.lowercase()
                if (!section.contains(name)) continue
                skills[skill] = Progress(section.getInt("$name.level"), section.getLong("$name.xp"))
            }
            if (skills.isNotEmpty()) progress[id] = skills
        }
    }

    /** Flush to disk if anything changed. Safe off the main thread (snapshot + plain file IO). */
    @Synchronized
    fun save() {
        if (!dirty) return
        dirty = false
        val yaml = YamlConfiguration()
        for ((id, skills) in progress) {
            for ((skill, p) in skills) {
                if (p.level == 0 && p.xp == 0L) continue
                val base = "skills.$id.${skill.name.lowercase()}"
                yaml.set("$base.level", p.level)
                yaml.set("$base.xp", p.xp)
            }
        }
        try {
            file.parentFile.mkdirs()
            yaml.save(file)
        } catch (e: Exception) {
            dirty = true // retry on the next flush
            logger.error("Failed to save skill progress to {}", file.path, e)
        }
    }

    companion object {
        /** XP to go from [level] to the next: 100, 120, 180, … quadratic so late levels are a grind. */
        fun xpForNext(level: Int): Long = 100L + 20L * level * level
    }
}

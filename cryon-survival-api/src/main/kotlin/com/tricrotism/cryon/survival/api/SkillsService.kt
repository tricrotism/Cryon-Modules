package com.tricrotism.cryon.survival.api

import java.util.*

/** The survival gamemode's five progression tracks. */
enum class Skill {
    MINING,
    WOODCUTTING,
    FARMING,
    FISHING,
    COMBAT,
}

/**
 * Skill progression, published into the Cryon `ServiceRegistry` by the `cryon-skills` module.
 * Gameplay events award XP; levels unlock perks (the module itself feeds a [SellModifier] into the
 * shop). Peers can [addXp] to plug new XP sources in from their own repo — e.g. a custom mob module
 * awarding COMBAT XP — without touching the skills engine.
 */
interface SkillsService {

    /** [player]'s current level in [skill] (0 for a fresh player). */
    fun level(player: UUID, skill: Skill): Int

    /** The sum of [player]'s levels across all skills — the gamemode's overall progression number. */
    fun totalLevel(player: UUID): Int

    /**
     * Award [amount] XP in [skill]. Level-ups (including several at once) are handled by the engine:
     * celebration message, sound, and the level reward. Amounts must be positive.
     */
    fun addXp(player: UUID, skill: Skill, amount: Long)
}

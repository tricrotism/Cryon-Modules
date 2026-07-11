package com.tricrotism.cryon.survival.api

import com.tricrotism.cryon.common.number.PackedDecimal
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * The shop's sell side, published into the Cryon `ServiceRegistry` by the `cryon-shop` module.
 *
 * Pricing is a base per-item price ([price], from the shop's config) times every registered
 * [SellModifier] — the extension seam: any feature repo can boost or tax sell value (the skills
 * module registers a level-based boost) with `services.find(SellService::class)` then [addModifier]
 * in `onEnable`. Whatever you add, [removeModifier] on disable.
 */
interface SellService {

    /** Base per-item sell price of [material], or null if the shop doesn't buy it. */
    fun price(material: Material): PackedDecimal?

    /**
     * What [player] would currently be paid for [amount] × [material] — base price times every
     * registered modifier. Null if the shop doesn't buy [material].
     */
    fun sellValue(player: Player, material: Material, amount: Int): PackedDecimal?

    /** Register a sell-price [modifier]. Remember to [removeModifier] on disable. */
    fun addModifier(modifier: SellModifier)

    /** Remove a previously-added modifier (by identity). */
    fun removeModifier(modifier: SellModifier)
}

/**
 * One multiplicative adjustment to sell value. Return `1.0` to leave the price alone. Multipliers
 * from all registered modifiers are multiplied together, so order doesn't matter. Keep it cheap —
 * this runs per material on every sell.
 */
fun interface SellModifier {
    fun multiplier(player: Player, material: Material): Double
}

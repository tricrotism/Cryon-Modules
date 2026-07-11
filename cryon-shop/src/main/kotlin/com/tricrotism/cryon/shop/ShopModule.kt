package com.tricrotism.cryon.shop

import com.tricrotism.cryon.common.flag.FeatureFlags
import com.tricrotism.cryon.common.locale.MessageService
import com.tricrotism.cryon.common.module.ModuleContext
import com.tricrotism.cryon.paper.api.PaperModule
import com.tricrotism.cryon.survival.api.EconomyService
import com.tricrotism.cryon.survival.api.SellService

/**
 * The sell shop for the survival gamemode: `/sell hand|all` turns gathered loot into Shards, priced
 * by an admin-editable table times the [SellService] modifier chain (that contract is how the skills
 * module boosts prices from its own repo). Proceeds go through [EconomyService] — without
 * `cryon-economy` installed the shop still loads, but `/sell` answers with a localized "unavailable".
 */
class ShopModule : PaperModule() {

    override val id = "shop"

    private lateinit var manager: ShopManager
    private lateinit var flags: FeatureFlags

    override fun onLoad(context: ModuleContext) {
        super.onLoad(context) // captures the PaperModuleContext — keep first
        manager = ShopManager(plugin, logger)
        manager.load()
        services.register(SellService::class, manager)
        flags = services.get(FeatureFlags::class)
        val messages = services.get(MessageService::class)
        registerCommands(
            SellCommands(manager, messages, flags, services),
            PriceCommands(manager, messages, flags),
        )
        logger.info("Shop loaded ({} sellable materials)", manager.priceCount())
    }

    override fun onEnable() {
        flags.register(FLAG_SELL)
        flags.register(FLAG_PRICE)
        if (services.find(EconomyService::class) == null) {
            logger.warn("cryon-economy not present — /sell will refuse until it's installed")
        }
    }

    companion object {
        /** Kill switches: the sell paths (hand + all), and the price lookup. */
        const val FLAG_SELL = "SHOP_SELL"
        const val FLAG_PRICE = "SHOP_PRICE"
    }
}

# cryon-shop

The sell shop for the survival gamemode: turn gathered loot into **Shards**. Implements and
publishes `SellService` (from `cryon-survival-api`).

## Commands

- `/sell hand` — sell the held stack. `/sell all` — sell everything sellable in your inventory.
  Bare `/sell` explains itself. (`cryon.shop.use`)
- `/price` (alias `/worth`) — what the held item currently sells for, per item and for the stack,
  with your modifiers applied.

## Pricing

Base per-item prices live in `plugins/Cryon/shop/prices.yml` (`MATERIAL_NAME: price`), written with
a starter table on first run — edit freely; invalid entries are skipped with a warning. The final
payout is `base × amount × every registered SellModifier`.

That modifier chain is the extension seam: any feature repo can boost or tax sell value by
resolving `SellService` and registering a `SellModifier` (that's how `cryon-skills` grants its
level-based bonus). A modifier that throws is logged and ignored, so a broken peer can't take
selling down.

## Peers

Proceeds deposit through `EconomyService`, resolved **per dispatch** so a hot-swapped economy is
picked up live; without `cryon-economy` the shop still loads and `/sell` answers with a localized
"unavailable".

## Feature flags

- `SHOP_SELL` — both sell paths (the economy-inflation kill switch).
- `SHOP_PRICE` — the price lookup.

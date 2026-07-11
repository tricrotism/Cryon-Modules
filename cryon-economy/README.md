# cryon-economy

The survival gamemode's currency: **Shards**. Implements and publishes `EconomyService` (from
`cryon-survival-api`) so any feature repo can pay or charge players — the shop deposits sell
proceeds, skills pays level-up rewards.

## Commands

- `/balance [player]` (alias `/bal`) — check a balance (`cryon.economy.use`); works from console.
- `/pay <player> <amount>` — send Shards to an online player. Amounts accept balance shorthand
  (`250`, `1.5k`, `2.35M`).
- `/baltop` — the ten richest players.
- `/eco give|take|set <player> <amount>` — the admin ledger (`cryon.economy.admin`); targets may be
  offline.

## Feature flags

- `ECONOMY_PAY` — the whole player-to-player transfer path (the dupe-glitch kill switch).
- `ECONOMY_BALTOP` — the leaderboard.

## Design notes

- Balances are **`PackedDecimal`** end to end — the framework's packed, zero-allocation scaling
  number — so the economy keeps working and formatting far past `Long`/`Double` scale.
- Persistence is **batched**: mutations only mark a dirty bit; an async timer (and the disable hook)
  flushes the whole ledger to `plugins/Cryon/economy/balances.yml`. No per-event file IO.
- Withdrawals are atomic (`computeIfPresent` on a `ConcurrentHashMap`) so concurrent spends can't
  double-withdraw.

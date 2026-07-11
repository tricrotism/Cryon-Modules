# ashvale-factions

The **Ashvale** factions core, Phase 1 of the [Ashvale gamemode](../../../Gamemode-Scopes/Ashvale)
design (`13-ROADMAP-AND-RISKS.md`). A Cryon feature module that owns faction membership, chunk
claims, and player power, and publishes `FactionService` (from `ashvale-factions-api`) so every other
Ashvale system builds on the same state.

## This slice

The core, end to end:

- `/f create <name>`: found a faction (name: 3 to 16 letters, digits, or underscore).
- `/f claim`: claim the chunk you're standing in, **gated by power** (a faction can hold at most
  `power` chunks).
- `/f unclaim`: release the chunk you're standing in back to wilderness.
- `/f info [name]`, `/f power`: read your (or another) faction's state and your power.
- **Power**: every faction member starts at 10, loses 2 on death, and regenerates 1/min back to 10.
  A dying member can push a faction over its land budget until it heals.
- **Claim protection**: non-members can't break, place, or open containers/doors on land another
  faction owns (`ashvale.factions.bypass` exempts staff). Explosions are deliberately *not* protected:
  breaching walls with TNT is the raiding pillar, governed by the explosion engine, not this guard.

Each independently-killable slice has its own feature flag: `FACTIONS_CREATE`, `FACTIONS_CLAIM`,
`FACTIONS_UNCLAIM`, `FACTIONS_POWER_LOSS`, `FACTIONS_PROTECTION` (`/cryon flag ...`).

## Persistence

Single-node: in-memory state with a batched flush to `plugins/Cryon/ashvale-factions/factions.yml`
(the `cryon-economy` pattern, no per-event IO). **Next step:** the design's cross-node topology
(`09-TECH-ARCHITECTURE.md`) needs faction state shared across the war and crystal nodes, which moves
this behind the core `Database` + `RedisStore`. The `FactionService` contract is storage-blind on
purpose, so that swap won't touch consumers.

## Build

Part of the `Cryon-Modules` composite build. From the repo root, `./gradlew buildAll` collects
`ashvale-factions-api-<v>.jar` into `build/api/` and `ashvale-factions-<v>.jar` into `build/modules/`.
Drop them into `plugins/Cryon/api/` and `plugins/Cryon/modules/` respectively.

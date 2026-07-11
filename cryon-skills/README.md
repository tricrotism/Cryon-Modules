# cryon-skills

Skill progression for the survival gamemode: five tracks — **Mining, Woodcutting, Farming, Fishing,
Combat** — levelled by just playing. Implements and publishes `SkillsService` (from
`cryon-survival-api`), so other repos can award XP from their own mechanics.

## How XP is earned

- **Mining** — breaking stone and ores (ancient debris pays best).
- **Woodcutting** — felling any log.
- **Farming** — harvesting *mature* crops (age-gated), melons and pumpkins.
- **Fishing** — every catch.
- **Combat** — kills: bosses ≫ hostiles > everything else. No PvP XP.

Ore/log/gourd XP is gated by an in-memory **placed-block tracker**, so place-and-rebreak can't farm
it (entries are evicted when a chunk unloads — the accepted trade-off for not persisting per-block
state). Crop XP is gated by maturity instead.

The XP-to-next-level curve is quadratic (`100 + 20·level²`), so early levels come fast and late ones
are a grind. Progress persists to `plugins/Cryon/skills/progress.yml` with batched dirty-bit saves.

## The feedback loops (optional peers)

- **Level rewards** — each level-up pays `25 × level` Shards through `EconomyService`, if
  `cryon-economy` is installed.
- **Sell boost** — a `SellModifier` registered into `cryon-shop`'s chain: **+0.5% sell value per
  total skill level, capped at +100%**. Removed again on disable.

Both are resolved with `services.find(...)` and skipped gracefully when the peer is absent.

## Commands

- `/skills [player]` (alias `/skill`, `cryon.skills.use`) — levels, XP progress, total level and the
  current sell bonus.

## Feature flags

One per XP source, plus one per feedback slice — any of them can be killed independently:
`SKILLS_MINING`, `SKILLS_WOODCUTTING`, `SKILLS_FARMING`, `SKILLS_FISHING`, `SKILLS_COMBAT`,
`SKILLS_SELL_BOOST`, `SKILLS_LEVEL_REWARD`.

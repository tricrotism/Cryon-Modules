# cryon-survival-api

The commons layer of the **survival economy gamemode** — the thin cross-module contract shared by
`cryon-economy`, `cryon-skills`, and `cryon-shop` (and any other repo that wants to hook the
gamemode). The gameplay loop the contracts describe: gather → earn skill XP → sell for **Shards** →
levels boost sell prices → climb `/baltop`.

- **`EconomyService`** — Shards balances as `PackedDecimal` (idle-scale, zero-allocation);
  `deposit`/`withdraw`/`transfer`/`top`. Implemented by `cryon-economy`.
- **`Skill` / `SkillsService`** — the five progression tracks; peers can `addXp` to plug in new XP
  sources from their own repo. Implemented by `cryon-skills`.
- **`SellService` / `SellModifier`** — the shop's sell side and its extension seam: register a
  modifier to boost or tax sell value (that's how skills feed the shop). Implemented by `cryon-shop`.

Build the jar and drop it into `plugins/Cryon/api/` so it loads once on the shared parent classloader
(every feature resolves the same `Class`). Every gamemode module `compileOnly`s it — never bundle it
into a feature jar. See the root `CLAUDE.md` → *Talking to another module*.

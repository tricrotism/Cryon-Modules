# ashvale-factions-api

The cross-module contract for the **Ashvale** factions core. A thin jar that lives on the shared
`plugins/Cryon/api/` classloader so every Ashvale feature, in this repo or another, resolves the
**same** `FactionService` type (bundling it into a feature jar instead = two `Class` objects =
`ClassCastException`).

Exposes:

- **`FactionService`**: the registered service. Create factions, claim chunks, query membership,
  claim ownership, and power.
- **`Faction`**: a live read-only view (id, name, leader, members, claims, power, roles).
- **`FactionId`**, **`ChunkKey`**, **`FactionRole`**: the shared value types.
- **`CreateResult`** / **`ClaimResult`**: sealed outcome types so callers exhaust the cases.

**Provider:** `ashvale-factions` registers an impl in `onLoad`
(`services.register(FactionService::class, manager)`).

**Consumer (any repo):** resolve in `onEnable` with `services.find(FactionService::class)`, using
**find, not get**, since factions may not be installed. Both provider and consumer `compileOnly` this
jar; the runtime copy comes from `plugins/Cryon/api/`.

Drop `build/libs/ashvale-factions-api-<v>.jar` into `plugins/Cryon/api/`.

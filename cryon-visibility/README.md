# cryon-visibility

A standalone Cryon feature (its own repo) that provides a **player-visibility engine** and ships
**vanish** as its first rule. Hidden players stay in the same world — they just aren't rendered for
viewers a rule hides them from. The Cryon core loads its jar at boot.

## The visibility engine

`VisibilityService` (published into the Cryon `ServiceRegistry`) decides who can see whom by
consulting registered `VisibilityRule`s and applies the result through Bukkit's per-player
`hidePlayer`/`showPlayer` tracker. A target is visible to a viewer unless **some rule hides it**.

```kotlin
fun interface VisibilityRule {
    fun hides(viewer: Player, target: Player): Boolean
}
```

Adding a new source of invisibility is one call — no engine change:

```kotlin
// e.g. the planned "hide players inside this region" system
visibility.addRule { viewer, target -> region.contains(target) && !region.contains(viewer) }
visibility.refreshAll()
```

## Vanish (the first rule)

- **Visibility** — a vanished player is hidden from everyone without `cryon.vanish.see`; staff with
  it still see each other. Re-applied on join so newcomers never see a ghost.
- **Ghost physics** (vanish-only — players hidden by other rules keep normal physics) — mobs
  forget/skip you (`EntityTargetLivingEntityEvent` + `affectsSpawning = false`), you pick nothing up
  (`EntityPickupItemEvent`), you don't keep others awake (`isSleepingIgnored = true`).
- **Silent presence** — your join and quit aren't broadcast, so switching servers or relogging while
  vanished doesn't announce you.
- **No messaging** — `/msg`/`/tell`/etc. to a vanished player you can't see answer "is not online"
  (vanish isn't revealed). Local to each server (that's all vanilla `/msg` reaches).

### Cross-server & persistence

`VanishStore` uses whatever infra is **connected** — each part is optional (`services.find`, no hard
dependency on either):

- **Redis** (`redis.enabled`) — propagates every toggle over pub/sub, so a staffer stays vanished as
  they switch servers and state is consistent network-wide while servers are up.
- **SQL** (`database.enabled`) — the durable source of truth: loaded into the vanish set on start (so a
  server that boots *after* a toggle still knows who's vanished) and written on every toggle, so vanish
  survives restarts.

With neither, vanish is per-server and clears on restart. With both, it's live-synced **and** durable.

> **Tab-complete caveat:** vanished players are removed from the **tab list / roster** (via
> `hidePlayer`), but vanilla command **autocomplete** (`/msg <tab>`) still suggests their names —
> filtering that needs packet interception (e.g. PacketEvents), which isn't wired into Cryon yet.
> Cryon-owned commands can filter their own name suggesters; a network-global tab list or cross-server
> `/msg` plugin should query the vanish state (`VisibilityService` / the synced set) directly.

### Commands & permissions

| Command            | Permission             | Effect                          |
|--------------------|------------------------|---------------------------------|
| `/vanish` (`/v`)   | `cryon.vanish.use`     | Toggle vanish on yourself       |
| `/vanish <player>` | `cryon.vanish.others`  | Toggle vanish on another player |
| `/vanish list`     | `cryon.vanish.see`     | List who is currently vanished  |
| —                  | `cryon.vanish.see`     | Passive: see vanished players   |

Acks are localized (`lang/en_US.properties`) in the recipient's resolved locale and play a sound.

## How a feature works

1. Depend on the Cryon API (`com.tricrotism:common` + `com.tricrotism:paper-api`) and Paper as
   **`compileOnly`** — the core provides them at runtime.
2. `class VisibilityModule : PaperModule()` with a **no-arg constructor**, declared in
   `src/main/resources/META-INF/services/com.tricrotism.cryon.common.module.Module`.
3. Build the plain jar and drop it into the core server's `plugins/Cryon/modules/`.

### Notes on the design

- **Vanish state is session-scoped** (in-memory, thread-safe) — it does not persist across rejoins.
  Cleared on quit; preserved across a `/cryon reload visibility` (reconciled on re-enable).
- **The command registers in `onLoad`, not `onEnable`.** Paper's `COMMANDS` lifecycle only accepts
  handlers during the core's enable window, and `onLoad` runs exactly once, so a runtime
  `/cryon reload visibility` (which re-runs `onEnable`) never double-registers it.
- **Cross-module consumption:** `VisibilityService`/`VisibilityRule` currently live in this jar, so
  only this jar's code can consume them. To register a rule from *another* isolated feature jar (e.g.
  a separate `cryon-areas` module), promote those two types into a shared, core-bundled artifact
  (`:paper-api` or a dedicated `cryon-visibility-api` jar both sides compile against) and register the
  impl under that type. Until then, add new rules inside this jar.
- **Folia:** the visibility passes call player-scoped Bukkit API on the event/command thread. If the
  network moves to Folia, route `refresh`/`refreshAll`/`revealAll` through the entity scheduler.

## Build

```bash
# one-time: publish the Cryon API to your local maven repo (run in the Cryon repo)
../Cryon/gradlew -p ../Cryon :common:publishToMavenLocal :paper-api:publishToMavenLocal

./gradlew build
# -> build/libs/cryon-visibility-1.0.0.jar  (drop into plugins/Cryon/modules/)
```

For production, publish/consume the API from `repo.striveservices.org` rather than mavenLocal.

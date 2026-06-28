# Cryon Modules — Feature Author Guide

This repo holds **Cryon feature modules** — independent jars the Cryon core discovers and loads at
boot from `plugins/Cryon/modules/`. Each feature is self-contained: it compiles against the published
Cryon API as `compileOnly` and ships a thin jar with only its own classes.

**The source of truth for house style is the core guide:** `../Cryon/CLAUDE.md` (Kotlin style,
messaging, items, scheduling, events, thread-safety, commit format). Read it. This file covers only
what's specific to *writing a module* and isn't repeated there. When you add module-author infra
(config framework, a shared test harness, a new `*-api` contract), document it here in the same pass.

---

## Anatomy of a feature

1. **`compileOnly` the API** — the core provides it at runtime, so never bundle it:
   ```kotlin
   compileOnly("com.tricrotism:common:1.0-SNAPSHOT")
   compileOnly("com.tricrotism:paper-api:1.0-SNAPSHOT")
   compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
   ```
   JDK 25 toolchain, Kotlin 2.4.20-Beta1. First-party group = `com.tricrotism.cryon`.
2. **`class Foo : PaperModule()` with a no-arg constructor** (it's instantiated via `ServiceLoader`).
3. **Declare it** in `src/main/resources/META-INF/services/com.tricrotism.cryon.common.module.Module`
   (one FQCN per line).
4. **Ship a `lang/en_US.properties`** bundle — the core auto-scans and registers it on load.
5. Build the plain `jar` → drop into the server's `plugins/Cryon/modules/`.

Start from **`cryon-spawn`** — the smallest end-to-end feature (one command, an i18n bundle, a
hand-rolled config, and consuming a peer's service). For the extensible service-plus-contract shape,
read the **`cryon-jumppads`** / **`cryon-jumppads-api`** pair.

---

## Lifecycle — and what `/cryon reload` actually does

`PaperModule` has three phases. Call `super` where noted.

- **`onLoad(context)`** (call `super.onLoad` first) — one-time setup: `services.register(...)`, read
  config, register commands. Runs for **every** module before any `onEnable`.
- **`onEnable()`** — wire listeners/tasks and consume peers (`services.find(...)`). Every peer's
  services are registered by now, regardless of jar order.
- **`onDisable()`** (call `super.onDisable()`) — tear down everything `onEnable` set up.

**`/cryon reload <id>` = `onDisable()` then `onEnable()` — it does NOT re-run `onLoad()`.** So:

| Put it in…  | …if it's                                                 | Survives reload?       |
|-------------|----------------------------------------------------------|------------------------|
| `onLoad`    | one-time: service registration, **command registration** | yes (not re-run)       |
| `onEnable`  | re-wireable: listeners, `Schedulers` tasks, peer rules   | re-created each reload |
| `onDisable` | teardown for everything in `onEnable`                    | —                      |

Registering a command in `onEnable` will **double-register or crash on reload** — Paper's `COMMANDS`
lifecycle only accepts handlers during the core's enable window. Always register commands in `onLoad`.

---

## Talking to another module — the `api/` contract layer

Feature jars are class-loaded in isolation and **cannot see each other's classes**. They intertwine
only through the **`ServiceRegistry`**, keyed by an interface that lives on the shared parent loader —
i.e. a thin **`*-api` jar dropped in `plugins/Cryon/api/`** (loaded once, so every feature resolves
the same type). Bundling the api into a feature jar instead = two `Class` objects = `ClassCastException`.

**Provider** (registers in `onLoad`):

```kotlin
services.register(VisibilityService::class, manager)
```

**Consumer** (resolves in `onEnable`, in any other repo):

```kotlin
val vis = services.find(VisibilityService::class) ?: return   // find, NOT get — peer may be absent
vis.addRule(myRule)
```

Rules:

- **`find` (nullable) for cross-module peers** — never `get`; an independent repo may not be installed.
  Degrade gracefully when it's null.
- **Whatever you add to a peer, remove on disable** — a rule, a handle, a registration. A lingering
  registration is a leak that survives your module being disabled. (See `removeRule` in the
  visibility API.)
- Both provider and consumer **`compileOnly`** the `*-api` jar; the runtime copy comes from `api/`.

Reference: **`cryon-visibility-api`** (contract) implemented by **`cryon-visibility`** and consumed by
**`cryon-spawn`**. Full details in `../Cryon/CLAUDE.md` → *Module System → Cross-module contracts*.

---

## Commands

`@Command`/`@Subcommand`/`@Permission`/`@Arg` classes, registered from `onLoad` via the `PaperModule`
helper:

```kotlin
override fun onLoad(context: ModuleContext) {
    super.onLoad(context)
    registerCommands(MyCommands(deps))   // COMMANDS-window registration + auto-gated on enabled-state
}
```

`registerCommands` gates every command on `isEnabled()`, so while the module is disabled
(`/cryon disable <id>`) its commands are unavailable (can't run, not tab-completed) and reappear on
re-enable — no per-command checks, no re-registration. Don't hand-roll the
`lifecycleManager.registerEventHandler(COMMANDS)` call anymore. Never `plugin.yml commands:` /
`CommandMap` / Cloud (broken on this Paper build). See the core guide's *Commands* section.

---

## House style (enforced — see the core guide for depth)

- **Messages:** `Component`s only, via `Mini`/`CommonMessages`; localize through `MessageService`
  (`messages.send(player, MessageType.X, "key", …)`). Ship `lang/<locale>.properties`. Never legacy
  `§`, never string interpolation into messages.
- **Items:** `ItemBuilder` / `Material.toItem()` (auto `<!i>`).
- **Scheduling:** `Schedulers.async/global/region/entity` — never raw `Bukkit.getScheduler()`. No
  Bukkit API off the main thread.
- **Events:** `Events.subscribe(...).filter{}.handler{}` → keep the `Subscription`, `unregister()` on
  disable. Or `listen(listener)` (auto-unregisters).
- **Sounds:** play a `Sound.*` on every player-facing action — silent feedback feels broken.
- **Thread-safety:** `ConcurrentHashMap` / `Collections.newSetFromMap(...)` for shared state.
- **Feature flags:** keep each distinct slice behind its own guard so it can be gated independently.

---

## Config

There is **no module-config framework yet** (and no per-module config-reload). If a feature needs
persistent settings, hand-roll it: a `YamlConfiguration` under `plugins/Cryon/<feature>/` via
`plugin.dataFolder`, read in `onLoad`. See `cryon-spawn`'s `Spawn` for the pattern. If/when a config
abstraction is added, document it here.

---

## Build & deploy

```bash
# one-time: publish the Cryon core API to mavenLocal so features can compile against it
../Cryon/gradlew -p ../Cryon :common:publishToMavenLocal :paper-api:publishToMavenLocal
```

**Build everything at once** — from this `Cryon-Modules/` root (a composite build, see
`settings.gradle.kts`):

```bash
./gradlew buildAll        # build + test every module; jars land in */build/libs/
./gradlew publishApis     # publish every *-api contract jar to mavenLocal
./gradlew cleanAll
```

`buildAll` substitutes each `*-api` dependency with its sibling build automatically, so consumers see
the live contract without a mavenLocal round-trip. **Add new modules to the root `settings.gradle.kts`
`includeBuild(...)` list.**

**Or build one module** standalone from its own dir (`./gradlew build` → `build/libs/<feature>.jar`).
Standalone consumer builds resolve a `*-api` from mavenLocal, so run `publishApis` (or the api's own
`publishToMavenLocal`) first.

Drop feature jars into `plugins/Cryon/modules/`, and any `*-api` contract jars into
`plugins/Cryon/api/`. For production, publish/consume the API from `repo.striveservices.org` instead
of mavenLocal.

---

## Reference modules in this repo

| Module                 | Shows                                                                                             |
|------------------------|---------------------------------------------------------------------------------------------------|
| `cryon-spawn`          | The starter: a command, i18n, config — and consuming a peer's service (`find` + graceful absence) |
| `cryon-visibility`     | A rule-driven engine + the `vanish` feature; publishes a service                                  |
| `cryon-visibility-api` | A thin cross-module contract jar for the `api/` layer                                             |
| `cryon-jumppads`       | An extensible engine: a priority-ordered, swappable strategy chain                                |
| `cryon-jumppads-api`   | The contract for extending another module from your own repo                                      |

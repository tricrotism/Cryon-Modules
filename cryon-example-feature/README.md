# cryon-example-feature

A standalone Cryon feature, in its own repo. The Cryon core plugin loads its jar at boot.

It exercises the shared utilities end to end: a `lang/en_US.properties` bundle registered with the
shared `MessageService` (i18n), a functional `Events` join subscription, a Folia-safe `Schedulers`
async heartbeat, `CryonNumber` (`pow` + suffix formatting), and a localized `ItemBuilder` token —
all torn down cleanly on disable. Delete/replace `ExampleFeatureModule` once you have real features.

## How a feature works

1. Depend on the Cryon API (`com.tricrotism:common` + `com.tricrotism:paper-api`) as **`compileOnly`**
   — the core provides them at runtime. Same for `paper-api` (Bukkit). Don't bundle them.
2. Write a `class X : PaperModule()` with a **no-arg constructor**.
3. Declare it in `src/main/resources/META-INF/services/com.tricrotism.cryon.common.module.Module`
   (one fully-qualified class name per line).
4. Build the plain jar and drop it into the core server's `plugins/Cryon/modules/`.

## Lifecycle (order-independent intertwining)

- `onLoad(context)` runs for **every** module first — publish services here:
  `services.register(MyApi::class, impl)`.
- `onEnable()` runs after all modules loaded — wire listeners/tasks and resolve peers here:
  `services.get<PeerApi>()` / `services.find<PeerApi>()`.
- `onDisable()` — teardown (listeners registered via `listen(...)` are cleaned up for you).

Because jars are class-loaded **in isolation**, features intertwine only through **shared API
interfaces**. A service interface must live in an artifact the core exposes to every feature
(`:common`, `:paper-api`, or a dedicated api jar both features compile against) — never reference
another feature's concrete classes.

## Build

```bash
# one-time: publish the Cryon API to your local maven repo (run in the Cryon repo)
../Cryon/gradlew -p ../Cryon :common:publishToMavenLocal :paper-api:publishToMavenLocal

./gradlew build
# -> build/libs/cryon-example-feature-1.0.0.jar  (drop into plugins/Cryon/modules/)
```

For production, publish/consume the API from `repo.striveservices.org` rather than mavenLocal.

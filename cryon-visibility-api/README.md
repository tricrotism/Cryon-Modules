# cryon-visibility-api

The cross-module **contract** for the Cryon visibility engine — just `VisibilityService` and
`VisibilityRule`, no implementation. It's a *shared API jar*: the core loads it onto the classloader
that parents every feature, so multiple feature repos resolve the same types and can intertwine
through the `ServiceRegistry`.

## Why it's a separate jar

Feature jars are class-loaded in isolation, so two repos can only share a type if the **core** loads
it (not either feature). The core scans `plugins/Cryon/api/*.jar` into one shared parent classloader,
so dropping this jar there exposes its interfaces to every feature at once.

## Use it

Both sides depend on it `compileOnly` (the runtime copy comes from `plugins/Cryon/api/`):

```kotlin
compileOnly("com.tricrotism:cryon-visibility-api:1.0.0")
```

- **Provider** (`cryon-visibility`): `class VisibilityManager : VisibilityService`, then
  `services.register(VisibilityService::class, manager)` in `onLoad`.
- **Consumer** (any other repo, e.g. `cryon-spawn`):

  ```kotlin
  override fun onEnable() {
      val vis = services.find(VisibilityService::class) ?: return   // optional dependency
      vis.addRule { viewer, target -> atSpawn(target) && !atSpawn(viewer) }
      Events.subscribe(PlayerMoveEvent::class.java)
          .filter { it.hasChangedBlock() }
          .handler { vis.refresh(it.player) }
  }
  ```

## Build & deploy

```bash
./gradlew build publishToMavenLocal
# -> build/libs/cryon-visibility-api-1.0.0.jar
#    drop into the server's plugins/Cryon/api/   (shared by all features)
#    publishToMavenLocal lets other feature repos compileOnly it
```

For production, publish/consume from `repo.striveservices.org` rather than mavenLocal.

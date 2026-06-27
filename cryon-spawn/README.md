# cryon-spawn

A starter spawn feature — and the worked example of **one feature repo consuming another's service**
across the Cryon module boundary. Set a spawn point, teleport to it, and (if the visibility module is
installed) keep the spawn crowd from rendering each other.

## What it does

- `/spawn` (`cryon.spawn.use`) — teleport to spawn.
- `/spawn set` (`cryon.spawn.set`) — set spawn to your current location (persisted to
  `plugins/Cryon/spawn/spawn.yml`).
- **Visibility integration (optional):** registers a `VisibilityRule` that hides players from each
  other while they're both inside the spawn region (16-block radius). Re-rendered as they cross the
  boundary.

## How the cross-module talk works

`cryon-spawn` never references `cryon-visibility`'s classes. Both repos `compileOnly` the shared
**`cryon-visibility-api`** jar (which lives in `plugins/Cryon/api/` at runtime, on the classloader
that parents every feature), and they meet through the `ServiceRegistry`:

```kotlin
override fun onEnable() {
    val vis = services.find(VisibilityService::class) ?: run {   // find, not get — it may be absent
        logger.info("cryon-visibility not present — spawn hiding disabled"); return
    }
    vis.addRule(spawn.rule)                                       // spawn.rule : VisibilityRule
    Events.subscribe(PlayerMoveEvent::class.java)
        .filter { it.hasChangedBlock() }
        .handler { vis.refresh(it.player) }
    vis.refreshAll()
}

override fun onDisable() {
    visibility?.let { it.removeRule(spawn.rule); it.refreshAll() } // clean teardown — no leaked rule
}
```

Key points:
- **`find`, not `get`** — the visibility module is a separate repo and might not be installed; spawn
  degrades gracefully (teleport still works, hiding doesn't).
- **Order-independent** — `onLoad` runs for every module before any `onEnable`, so if visibility is
  installed its service is always registered by the time spawn enables.
- **Remove your rule on disable** — `removeRule` + `refreshAll` so a disabled/reloaded spawn module
  stops affecting visibility.

## Build & deploy

```bash
# one-time: publish the Cryon API + the visibility contract to mavenLocal
../Cryon/gradlew -p ../Cryon :common:publishToMavenLocal :paper-api:publishToMavenLocal
../cryon-visibility-api/gradlew -p ../cryon-visibility-api publishToMavenLocal

./gradlew build
# -> build/libs/cryon-spawn-1.0.0.jar   ->  plugins/Cryon/modules/
```

For the visibility hook to light up, also have `cryon-visibility-api-*.jar` in `plugins/Cryon/api/`
and `cryon-visibility-*.jar` in `plugins/Cryon/modules/`.

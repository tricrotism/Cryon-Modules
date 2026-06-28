# cryon-jumppads

Jump pads: a pad is a block, and stepping onto it launches you. **Where** you launch is decided by a
priority-ordered chain of launch strategies, so the mechanic is easy to extend.

## Default behaviour

The module seeds the chain with two strategies, consulted in order — the first to return a velocity wins:

1. **Targeted** — if you're looking at a block within range, you're arced onto the top of it (a
   constant-gravity ballistic solve). This is the "launch me *there*" behaviour.
2. **Directional** — otherwise, you're flung along your look vector at a fixed power, with a guaranteed
   bit of lift. This is the plain launch-pad fallback.

So: look at nothing and it's a launch pad in the direction you face; look at a block and it sends you
to that block instead.

A launched rider gets a few seconds of fall immunity so the landing doesn't punish them.

## Authoring pads

`/jumppad add | remove | list` (alias `/jp`, permission `cryon.jumppads.admin`). `add`/`remove` act on
the block you're looking at, falling back to the block under your feet. Pads persist to
`plugins/Cryon/jumppads/pads.yml`.

## Tuning

`plugins/Cryon/jumppads/config.yml` (written with defaults on first load) controls launch power,
gravity, arc flight-time, ray-trace range, the re-trigger cooldown, and the fall-immunity window. The
arc is a simple constant-gravity solution, not a Minecraft-exact one — air drag makes it approximate;
nudge `launch.ticks-per-block` / `launch.gravity` if pads over- or under-shoot.

## Extending it

The engine publishes **`JumpPadService`** (in the `cryon-jumppads-api` jar). From any other feature
repo, resolve it in `onEnable` and add your own `LaunchStrategy` in front of the defaults:

```kotlin
val pads = services.find(JumpPadService::class) ?: return   // find, not get — it may be absent
pads.addStrategy(myStrategy)   // first non-null velocity wins
// ...and remove it on disable.
```

`cryon-jumppads-api` must be in `plugins/Cryon/api/`; both this module and any consumer `compileOnly`
it. See the root `CLAUDE.md` → *Talking to another module*.

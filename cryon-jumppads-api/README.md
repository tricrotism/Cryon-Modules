# cryon-jumppads-api

The thin cross-module contract for the jump-pad mechanic. Implemented by **`cryon-jumppads`** and
consumable by any other feature repo that wants to extend how pads launch players.

- **`LaunchStrategy`** — one way to fling a player off a pad. The engine consults registered
  strategies in order and uses the first that returns a velocity. Register yours in front of the
  defaults to take precedence.
- **`JumpPadService`** — published into the Cryon `ServiceRegistry`; `addStrategy`/`addPad` to extend,
  `isPad`/`padCount` to query, `launch` to trigger directly.

Build the jar and drop it into `plugins/Cryon/api/` so it loads once on the shared parent classloader
(every feature resolves the same `Class`). Both provider and consumers `compileOnly` it — never bundle
it into a feature jar. See the root `CLAUDE.md` → *Talking to another module*.

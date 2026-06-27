# Translating Cryon modules

Each module's player-facing text lives in `<module>/src/main/resources/lang/<locale>.properties`
(MiniMessage templates). The `crowdin.yml` glob covers **every** module, so a new module is picked up
with no config change. Translations are auto-registered at runtime by the core's `LangScanner`.

## One-time setup

1. Create a Crowdin project — file format **Java Properties**, source language **English**. (Use the
   core's project or a separate one; both work — separate keeps module and core strings apart.)
2. Add two repo secrets (Settings → Secrets and variables → Actions):
   - `CROWDIN_PROJECT_ID`
   - `CROWDIN_PERSONAL_TOKEN`
3. **Protect the MiniMessage tags** so `<player>`, `<highlight>`, … aren't translated. Add a custom
   placeholder regex in the project settings:
   ```
   <[^>]+>
   ```

## How it flows

- Edit/add keys in any module's `en_US.properties` → push to `master` → sources upload to Crowdin.
- Daily (and via *Run workflow*) the workflow opens an `[i18n] Sync translations from Crowdin` PR with
  completed translations. Review, merge, and they ship in the next module build.

Missing keys render `⟨key⟩` and fall back through the locale chain, so partial translations are safe.

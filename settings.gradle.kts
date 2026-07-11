rootProject.name = "Cryon-Modules"

// Composite build: each module stays a fully independent build (its own settings.gradle.kts +
// gradlew), included here only so `./gradlew buildAll` can drive them together. A consumer's
// dependency on a sibling `*-api` jar (e.g. cryon-visibility-api) is substituted with that included
// build's output automatically — no mavenLocal round-trip needed when building from this root.
//
// Add a module here when you create it. The core API (com.tricrotism:common / :paper-api) is NOT
// included — publish it once from the Cryon repo:
//   ../Cryon/gradlew -p ../Cryon :common:publishToMavenLocal :paper-api:publishToMavenLocal
includeBuild("cryon-visibility-api")
includeBuild("cryon-visibility")
includeBuild("cryon-spawn")
includeBuild("cryon-jumppads-api")
includeBuild("cryon-jumppads")
includeBuild("cryon-survival-api")
includeBuild("cryon-economy")
includeBuild("cryon-skills")
includeBuild("cryon-shop")

// Ashvale — hardcore siege-factions gamemode (D:\Gamemode-Scopes\Ashvale). Phase 1 foundation.
includeBuild("ashvale-factions-api")
includeBuild("ashvale-factions")

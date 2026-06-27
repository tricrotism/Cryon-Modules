rootProject.name = "cryon-modules"

// Composite build: each module stays a fully independent build (its own settings.gradle.kts +
// gradlew), included here only so `./gradlew buildAll` can drive them together. A consumer's
// dependency on a sibling `*-api` jar (e.g. cryon-visibility-api) is substituted with that included
// build's output automatically — no mavenLocal round-trip needed when building from this root.
//
// Add a module here when you create it. The core API (com.tricrotism:common / :paper-api) is NOT
// included — publish it once from the Cryon repo:
//   ../Cryon/gradlew -p ../Cryon :common:publishToMavenLocal :paper-api:publishToMavenLocal
includeBuild("cryon-example-feature")
includeBuild("cryon-visibility-api")
includeBuild("cryon-visibility")
includeBuild("cryon-spawn")

plugins {
    kotlin("jvm") version "2.4.20-Beta1"
}

repositories {
    mavenLocal() // resolves the Cryon API published with `./gradlew publishToMavenLocal`
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // The Cryon module framework — provided at runtime by the core plugin, so compileOnly.
    compileOnly("com.tricrotism:common:1.0-SNAPSHOT")
    compileOnly("com.tricrotism:paper-api:1.0-SNAPSHOT")
    // The visibility contract — the SAME jar cryon-visibility implements. Provided at runtime from
    // the core's plugins/Cryon/api/ layer, so compileOnly. This is how a separate repo intertwines.
    compileOnly("com.tricrotism:cryon-visibility-api:1.0.0")
    // Paper API — also provided by the server at runtime.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(25)
}

// The plain `jar` is the deployable artifact: drop build/libs/cryon-spawn-<v>.jar into the core
// server's plugins/Cryon/modules/. cryon-visibility-api must be in plugins/Cryon/api/ for the
// visibility integration to light up (the module still works without it — see SpawnModule).

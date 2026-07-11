plugins {
    kotlin("jvm") version "2.4.20-Beta1"
    `maven-publish`
}

repositories {
    mavenLocal() // resolves the Cryon core API (:common) for PackedDecimal
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // PackedDecimal — the framework's scaling number crosses the contract. Provided at runtime by
    // the core (bundled in the plugin jar, visible to the shared api/ loader), so compileOnly.
    compileOnly("com.tricrotism:common:1.0-SNAPSHOT")
    // Only the platform types the contracts reference (Player, Material). Provided at runtime.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(25)
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}

// The built jar is BOTH the compile contract the survival modules `compileOnly`, and the runtime
// artifact: drop build/libs/cryon-survival-api-<v>.jar into the core server's plugins/Cryon/api/ so
// the loader puts it on the shared parent classloader for every feature to share.

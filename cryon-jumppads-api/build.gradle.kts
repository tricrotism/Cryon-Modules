plugins {
    kotlin("jvm") version "2.4.20-Beta1"
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Only the platform types the contracts reference (Player, Location, Vector). Provided at runtime.
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

// The built jar is BOTH the compile contract other feature repos `compileOnly`, and the runtime
// artifact: drop build/libs/cryon-jumppads-api-<v>.jar into the core server's plugins/Cryon/api/ so
// the loader puts it on the shared parent classloader for every feature to share.

plugins {
    kotlin("jvm") version "2.4.20-Beta1"
    `maven-publish`
}

repositories {
    mavenCentral()
}

// The Ashvale factions contract crosses only Kotlin/JDK types (UUID) at this stage, so it needs no
// Cryon or Paper API on its compile path. When the contract grows a faction value (`PackedDecimal`
// for `/f top`) or Paper types, add them as `compileOnly` here and document it in the module README.

kotlin {
    jvmToolchain(25)
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}

// The built jar is BOTH the compile contract the Ashvale modules `compileOnly`, and the runtime
// artifact: drop build/libs/ashvale-factions-api-<v>.jar into the core server's plugins/Cryon/api/
// so the loader puts it on the shared parent classloader for every feature to share.

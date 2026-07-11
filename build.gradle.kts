// Aggregator tasks over the included module builds (see settings.gradle.kts).
//   ./gradlew buildAll      build + test every module, then collect the jars into build/:
//                             build/api/      the *-api contract jars  (-> plugins/Cryon/api/)
//                             build/modules/  the feature impl jars    (-> plugins/Cryon/modules/)
//   ./gradlew publishApis   publish every *-api contract jar to mavenLocal
//   ./gradlew cleanAll      clean every module

val apiBuilds = gradle.includedBuilds.filter { it.name.endsWith("-api") }
val implBuilds = gradle.includedBuilds.filterNot { it.name.endsWith("-api") }

val collectApiJars = tasks.register<Sync>("collectApiJars") {
    group = "build"
    description = "Copy every *-api contract jar into build/api/."
    dependsOn(apiBuilds.map { it.task(":build") })
    apiBuilds.forEach { from(it.projectDir.resolve("build/libs")) }
    include("*.jar")
    exclude("*-sources.jar", "*-javadoc.jar")
    into(layout.buildDirectory.dir("api"))
}

val collectModuleJars = tasks.register<Sync>("collectModuleJars") {
    group = "build"
    description = "Copy every feature impl jar into build/modules/."
    dependsOn(implBuilds.map { it.task(":build") })
    implBuilds.forEach { from(it.projectDir.resolve("build/libs")) }
    include("*.jar")
    exclude("*-sources.jar", "*-javadoc.jar")
    into(layout.buildDirectory.dir("modules"))
}

tasks.register("buildAll") {
    group = "build"
    description = "Assemble and test every module, collecting jars into build/api/ and build/modules/."
    dependsOn(collectApiJars, collectModuleJars)
}

tasks.register("cleanAll") {
    group = "build"
    description = "Clean every module."
    dependsOn(gradle.includedBuilds.map { it.task(":clean") })
}

tasks.register("publishApis") {
    group = "publishing"
    description = "Publish every *-api contract jar to mavenLocal (so standalone module builds resolve it)."
    dependsOn(
        gradle.includedBuilds
            .filter { it.name.endsWith("-api") }
            .map { it.task(":publishToMavenLocal") }
    )
}

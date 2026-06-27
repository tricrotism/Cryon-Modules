// Aggregator tasks over the included module builds (see settings.gradle.kts).
//   ./gradlew buildAll      build + test every module      (jars in */build/libs/)
//   ./gradlew publishApis   publish every *-api contract jar to mavenLocal
//   ./gradlew cleanAll      clean every module

tasks.register("buildAll") {
    group = "build"
    description = "Assemble and test every module."
    dependsOn(gradle.includedBuilds.map { it.task(":build") })
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

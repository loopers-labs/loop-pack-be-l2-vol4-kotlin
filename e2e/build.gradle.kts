import org.springframework.boot.gradle.tasks.bundling.BootJar

evaluationDependsOn(":apps:commerce-api")
evaluationDependsOn(":apps:commerce-streamer")

val commerceApiBootJar = project(":apps:commerce-api").tasks.named<BootJar>("bootJar")
val commerceStreamerBootJar = project(":apps:commerce-streamer").tasks.named<BootJar>("bootJar")

dependencies {
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.apache.kafka:kafka-clients")
}

tasks.test {
    dependsOn(commerceApiBootJar, commerceStreamerBootJar)

    doFirst {
        systemProperty(
            "commerce.api.jar",
            commerceApiBootJar.get().archiveFile.get().asFile.absolutePath,
        )
        systemProperty(
            "commerce.streamer.jar",
            commerceStreamerBootJar.get().archiveFile.get().asFile.absolutePath,
        )
    }
}

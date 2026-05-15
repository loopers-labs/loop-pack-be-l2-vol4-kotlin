import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:account-application"))
    implementation(project(":modules:account-persistence"))
    implementation(project(":modules:account-security"))
    implementation(project(":supports:error"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))
    implementation(project(":supports:web"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    testRuntimeOnly("com.h2database:h2")
}

tasks.named<BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}

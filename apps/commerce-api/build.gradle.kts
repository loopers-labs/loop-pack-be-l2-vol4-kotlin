plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":modules:kafka"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")
    implementation("io.github.resilience4j:resilience4j-retry:${project.properties["resilience4jVersion"]}")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:${project.properties["resilience4jVersion"]}")
    implementation("io.github.resilience4j:resilience4j-timelimiter:${project.properties["resilience4jVersion"]}")

    // bcrypt (no Spring Security)
    implementation("at.favre.lib:bcrypt:0.10.2")

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    // test-fixtures
    integrationTestImplementation(testFixtures(project(":modules:jpa")))
    integrationTestImplementation(testFixtures(project(":modules:redis")))
}

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

    // security-crypto (BCrypt)
    implementation("org.springframework.security:spring-security-crypto")

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // external integration (PG) - Feign + Resilience4j
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
    testImplementation(testFixtures(project(":modules:kafka")))

    // async assertion
    testImplementation("org.awaitility:awaitility")
}

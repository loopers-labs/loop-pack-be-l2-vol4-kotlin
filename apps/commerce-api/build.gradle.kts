plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:kafka"))
    implementation(project(":modules:redis"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:${project.properties["resilience4jVersion"]}")
    implementation("io.github.resilience4j:resilience4j-retry:${project.properties["resilience4jVersion"]}")

    // security (BCrypt)
    implementation("org.springframework.security:spring-security-crypto")

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
    testImplementation("org.testcontainers:mysql")
}

// pg-simulator 를 실제 컨테이너로 띄우는 Live 통합 테스트(PaymentPgLiveIntegrationTest)용 설정.
// pg-simulator 모듈 코드는 변경하지 않고, 빌드된 bootJar 산출물만 컨테이너로 구동한다.
tasks.test {
    dependsOn(":apps:pg-simulator:bootJar")
    systemProperty(
        "pg.simulator.libs",
        project(":apps:pg-simulator").layout.buildDirectory.dir("libs").get().asFile.absolutePath,
    )
}

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
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // feign (PG 연동)
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    // resilience4j (Retry, CircuitBreaker) - 외부 PG 연동 회복 전략
    implementation("io.github.resilience4j:resilience4j-spring-boot3:${project.properties["resilience4jVersion"]}")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))

    // practice: standalone MySQL testcontainer (프로젝트와 무관한 연습용)
    testImplementation("org.testcontainers:mysql")

    // archunit
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // PG 연동 실제 타임아웃/회복전략 검증용 (실제 HTTP 지연 응답 시뮬레이션)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

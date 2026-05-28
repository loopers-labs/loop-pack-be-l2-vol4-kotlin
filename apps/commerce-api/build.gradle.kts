plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    // add-ons
    implementation(project(":modules:persistence-core"))
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
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
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    testRuntimeOnly("com.h2database:h2")
}

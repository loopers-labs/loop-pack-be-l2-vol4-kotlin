plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    // add-ons
    implementation(project(":modules:persistence-core"))
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":modules:kafka"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    testRuntimeOnly("com.h2database:h2")
    testImplementation(testFixtures(project(":modules:redis")))
}

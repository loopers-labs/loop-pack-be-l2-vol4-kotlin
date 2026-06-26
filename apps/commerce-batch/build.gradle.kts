plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // batch
    implementation("org.springframework.boot:spring-boot-starter-batch")
    testImplementation("org.springframework.batch:spring-batch-test")

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    // test-fixtures
    integrationTestImplementation(testFixtures(project(":modules:jpa")))
    integrationTestImplementation(testFixtures(project(":modules:redis")))
}

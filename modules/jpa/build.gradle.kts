plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
    `java-test-fixtures`
}

dependencies {
    api(project(":modules:persistence-core"))

    // jpa
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    // jdbc-mysql
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.testcontainers:mysql")

    testFixturesImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testFixturesImplementation("org.testcontainers:mysql")
}

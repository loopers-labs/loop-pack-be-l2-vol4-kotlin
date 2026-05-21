plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    implementation(project(":modules:account-domain"))
    implementation(project(":modules:jpa"))

    testRuntimeOnly("com.h2database:h2")
}

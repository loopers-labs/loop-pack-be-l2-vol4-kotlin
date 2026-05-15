plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    api(project(":modules:persistence-core"))
    api(project(":supports:error"))
}

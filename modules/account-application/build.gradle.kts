dependencies {
    implementation(project(":modules:account-domain"))
    implementation(project(":modules:account-persistence"))
    implementation(project(":supports:error"))

    implementation("org.springframework:spring-tx")
}

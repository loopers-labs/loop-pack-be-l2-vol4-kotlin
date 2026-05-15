dependencies {
    implementation(project(":modules:account-application"))
    implementation(project(":modules:account-domain"))
    implementation(project(":supports:error"))
    implementation(project(":supports:web"))

    implementation("org.springframework.boot:spring-boot-starter-security")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
}

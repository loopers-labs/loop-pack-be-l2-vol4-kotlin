dependencies {
    implementation(project(":supports:error"))
    implementation(project(":supports:jackson"))

    implementation("org.springframework:spring-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
}

dependencies {
    implementation(project(":supports:error"))
    implementation(project(":supports:jackson"))

    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework:spring-tx")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
}

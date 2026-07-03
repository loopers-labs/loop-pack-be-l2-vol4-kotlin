package com.loopers.testcontainers

import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.MountableFile
import org.testcontainers.utility.DockerImageName
import java.nio.file.Path
import kotlin.io.path.exists

@Configuration
class MySqlTestContainersConfig {
    companion object {
        private val mySqlContainer: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .apply {
                withDatabaseName("loopers")
                withUsername("test")
                withPassword("test")
                withExposedPorts(3306)
                withCopyFileToContainer(
                    MountableFile.forHostPath(findSchemaPath()),
                    "/docker-entrypoint-initdb.d/01-schema.sql",
                )
                withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_general_ci",
                    "--skip-character-set-client-handshake",
                )
                start()
            }

        init {
            val mySqlJdbcUrl = mySqlContainer.let { "jdbc:mysql://${it.host}:${it.firstMappedPort}/${it.databaseName}" }
            System.setProperty("datasource.mysql-jpa.main.jdbc-url", mySqlJdbcUrl)
            System.setProperty("datasource.mysql-jpa.main.username", mySqlContainer.username)
            System.setProperty("datasource.mysql-jpa.main.password", mySqlContainer.password)
        }

        private fun findSchemaPath(): Path {
            var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
            while (current.parent != null) {
                val schemaPath = current.resolve("docker/mysql/init/01-schema.sql")
                if (schemaPath.exists()) {
                    return schemaPath
                }
                current = current.parent
            }
            throw IllegalStateException("Cannot find docker/mysql/init/01-schema.sql from ${System.getProperty("user.dir")}")
        }
    }
}

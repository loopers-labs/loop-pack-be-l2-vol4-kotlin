package com.loopers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import javax.sql.DataSource

@SpringBootTest(
    properties = [
        "spring.profiles.active=local",
        "datasource.mysql-jpa.main.driver-class-name=org.h2.Driver",
        "datasource.mysql-jpa.main.jdbc-url=jdbc:h2:mem:loopers-local-test;MODE=MySQL;DATABASE_TO_UPPER=false;NON_KEYWORDS=USER,VALUE;DB_CLOSE_DELAY=-1",
        "datasource.mysql-jpa.main.username=sa",
        "datasource.mysql-jpa.main.password=",
        "spring.jpa.hibernate.ddl-auto=create",
    ],
)
class LocalInMemoryInfrastructureContextTest @Autowired constructor(
    private val dataSource: DataSource,
    private val environment: Environment,
) {
    @Test
    fun localProfileUsesInMemoryJpa() {
        dataSource.connection.use { connection ->
            assertThat(connection.metaData.url).startsWith("jdbc:h2:mem:")
        }
    }

    @Test
    fun localProfileDoesNotPointKafkaAtDocker() {
        assertThat(environment.getProperty("spring.kafka.bootstrap-servers"))
            .isNotEqualTo("localhost:19092")
    }
}

package com.loopers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class CommerceStreamerApplicationConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "spring.config.location=classpath:/application.yml",
            "spring.profiles.active=test",
        )

    @Test
    fun `streamer_애플리케이션_이름을_kafka_client_id로_사용한다`() {
        contextRunner.run { context ->
            assertThat(context.environment.getProperty("spring.application.name"))
                .isEqualTo("commerce-streamer")
            assertThat(context.environment.getProperty("spring.kafka.client-id"))
                .isEqualTo("commerce-streamer")
        }
    }
}

package com.loopers.support.outbox.relay

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class OutboxRelayIntegrationTestConfig {
    @Bean
    @Primary
    fun recordingOutboxEventPublisher(): RecordingOutboxEventPublisher = RecordingOutboxEventPublisher()
}

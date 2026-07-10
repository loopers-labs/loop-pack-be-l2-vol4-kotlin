package com.loopers.testcontainers

import org.springframework.context.annotation.Configuration
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

@Configuration
class KafkaTestContainersConfig {
    companion object {
        private val kafkaContainer: ConfluentKafkaContainer =
            ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                .apply {
                    withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
                    start()
                }

        init {
            System.setProperty("spring.kafka.bootstrap-servers", kafkaContainer.bootstrapServers)
        }
    }
}

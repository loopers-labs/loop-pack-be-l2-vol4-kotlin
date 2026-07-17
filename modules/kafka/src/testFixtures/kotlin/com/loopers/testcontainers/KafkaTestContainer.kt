package com.loopers.testcontainers

import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

object KafkaTestContainer {
    private val container: KafkaContainer by lazy {
        KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))
            .apply { start() }
    }

    val bootstrapServers: String
        get() = container.bootstrapServers
}

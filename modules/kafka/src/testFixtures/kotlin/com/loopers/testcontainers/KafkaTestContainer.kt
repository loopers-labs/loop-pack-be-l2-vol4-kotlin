package com.loopers.testcontainers

import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

/**
 * 테스트용 Kafka 브로커(Testcontainers).
 * 최초로 [bootstrapServers] 에 접근하는 시점에 컨테이너가 시작된다.
 * Kafka 가 필요한 테스트에서만 @DynamicPropertySource 로 참조해 사용한다.
 */
object KafkaTestContainer {
    private val container: KafkaContainer =
        KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).apply { start() }

    val bootstrapServers: String
        get() = container.bootstrapServers
}

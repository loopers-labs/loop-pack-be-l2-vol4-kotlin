package com.loopers.config.kafka

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.ProducerFactory
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("local")
@SpringBootTest(classes = [LocalInMemoryKafkaConfig::class])
class LocalInMemoryKafkaConfigTest @Autowired constructor(
    private val producerFactory: ProducerFactory<Any, Any>,
    private val consumerFactory: ConsumerFactory<Any, Any>,
) {
    @Test
    fun localProfileUsesInMemoryKafkaFactories() {
        assertThat(producerFactory).isInstanceOf(InMemoryKafkaProducerFactory::class.java)
        assertThat(consumerFactory).isInstanceOf(InMemoryKafkaConsumerFactory::class.java)
    }
}

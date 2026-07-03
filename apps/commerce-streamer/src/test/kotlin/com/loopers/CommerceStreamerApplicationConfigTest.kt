package com.loopers

import com.loopers.config.kafka.CommerceKafkaTopicConfig
import com.loopers.config.kafka.ProductMetricsTopicProperties
import org.apache.kafka.clients.admin.NewTopic
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

    @Test
    fun `상품_metrics_이벤트_topic_이름이_설정된다`() {
        contextRunner.run { context ->
            assertThat(context.environment.getProperty("commerce-events.product-metrics.catalog-topic-name"))
                .isEqualTo("catalog-events")
            assertThat(context.environment.getProperty("commerce-events.product-metrics.order-topic-name"))
                .isEqualTo("order-events")
        }
    }

    @Test
    fun `상품_metrics_이벤트_topic은_NewTopic_bean으로_명시_생성된다`() {
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(CommerceKafkaTopicConfig::class.java)
            .withPropertyValues(
                "spring.config.location=classpath:/application.yml",
                "spring.profiles.active=test",
            )
            .run { context ->
                val catalogTopic = context.getBean("catalogEventsTopic", NewTopic::class.java)
                val catalogDltTopic = context.getBean("catalogEventsDltTopic", NewTopic::class.java)
                val orderTopic = context.getBean("orderEventsTopic", NewTopic::class.java)
                val orderDltTopic = context.getBean("orderEventsDltTopic", NewTopic::class.java)
                val properties = context.getBean(ProductMetricsTopicProperties::class.java)

                assertThat(properties.catalogTopicName).isEqualTo("catalog-events")
                assertThat(properties.catalogDltTopicName).isEqualTo("catalog-events.DLT")
                assertThat(properties.orderTopicName).isEqualTo("order-events")
                assertThat(properties.orderDltTopicName).isEqualTo("order-events.DLT")
                assertThat(properties.partitions).isEqualTo(3)
                assertThat(properties.replicas).isEqualTo(1)
                assertThat(catalogTopic.name()).isEqualTo("catalog-events")
                assertThat(catalogTopic.numPartitions()).isEqualTo(3)
                assertThat(catalogTopic.replicationFactor()).isEqualTo(1.toShort())
                assertThat(catalogDltTopic.name()).isEqualTo("catalog-events.DLT")
                assertThat(orderTopic.name()).isEqualTo("order-events")
                assertThat(orderDltTopic.name()).isEqualTo("order-events.DLT")
            }
    }
}

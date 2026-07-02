package com.loopers

import com.loopers.config.kafka.CommerceKafkaTopicConfig
import com.loopers.config.kafka.LikeCountTopicProperties
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
    fun `좋아요_수_이벤트_topic_이름이_설정된다`() {
        contextRunner.run { context ->
            assertThat(context.environment.getProperty("commerce-events.like-count.topic-name"))
                .isEqualTo("commerce.like-count-events.v1")
            assertThat(context.environment.getProperty("commerce-events.like-count.dlt-topic-name"))
                .isEqualTo("commerce.like-count-events.v1.DLT")
        }
    }

    @Test
    fun `좋아요_수_이벤트_topic은_NewTopic_bean으로_명시_생성된다`() {
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(CommerceKafkaTopicConfig::class.java)
            .withPropertyValues(
                "spring.config.location=classpath:/application.yml",
                "spring.profiles.active=test",
            )
            .run { context ->
                val topic = context.getBean("likeCountEventsTopic", NewTopic::class.java)
                val dltTopic = context.getBean("likeCountEventsDltTopic", NewTopic::class.java)
                val properties = context.getBean(LikeCountTopicProperties::class.java)

                assertThat(properties.topicName).isEqualTo("commerce.like-count-events.v1")
                assertThat(properties.dltTopicName).isEqualTo("commerce.like-count-events.v1.DLT")
                assertThat(properties.partitions).isEqualTo(3)
                assertThat(properties.replicas).isEqualTo(1)
                assertThat(topic.name()).isEqualTo("commerce.like-count-events.v1")
                assertThat(topic.numPartitions()).isEqualTo(3)
                assertThat(topic.replicationFactor()).isEqualTo(1.toShort())
                assertThat(dltTopic.name()).isEqualTo("commerce.like-count-events.v1.DLT")
                assertThat(dltTopic.numPartitions()).isEqualTo(3)
                assertThat(dltTopic.replicationFactor()).isEqualTo(1.toShort())
            }
    }
}

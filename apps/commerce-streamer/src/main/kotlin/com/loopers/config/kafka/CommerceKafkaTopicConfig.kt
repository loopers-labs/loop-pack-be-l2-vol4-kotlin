package com.loopers.config.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
@EnableConfigurationProperties(LikeCountTopicProperties::class)
class CommerceKafkaTopicConfig {
    @Bean
    fun likeCountEventsTopic(properties: LikeCountTopicProperties): NewTopic {
        return TopicBuilder.name(properties.topicName)
            .partitions(properties.partitions)
            .replicas(properties.replicas)
            .build()
    }

    @Bean
    fun likeCountEventsDltTopic(properties: LikeCountTopicProperties): NewTopic {
        return TopicBuilder.name(properties.dltTopicName)
            .partitions(properties.partitions)
            .replicas(properties.replicas)
            .build()
    }
}

@ConfigurationProperties(prefix = "commerce-events.like-count")
data class LikeCountTopicProperties(
    val topicName: String,
    val dltTopicName: String,
    val partitions: Int,
    val replicas: Int,
)

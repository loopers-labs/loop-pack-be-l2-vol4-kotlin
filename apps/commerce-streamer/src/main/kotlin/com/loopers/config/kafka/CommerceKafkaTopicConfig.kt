package com.loopers.config.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
@EnableConfigurationProperties(ProductMetricsTopicProperties::class)
class CommerceKafkaTopicConfig {
    @Bean
    fun catalogEventsTopic(properties: ProductMetricsTopicProperties): NewTopic {
        return TopicBuilder.name(properties.catalogTopicName)
            .partitions(properties.partitions)
            .replicas(properties.replicas)
            .build()
    }

    @Bean
    fun catalogEventsDltTopic(properties: ProductMetricsTopicProperties): NewTopic {
        return TopicBuilder.name(properties.catalogDltTopicName)
            .partitions(properties.partitions)
            .replicas(properties.replicas)
            .build()
    }

    @Bean
    fun orderEventsTopic(properties: ProductMetricsTopicProperties): NewTopic {
        return TopicBuilder.name(properties.orderTopicName)
            .partitions(properties.partitions)
            .replicas(properties.replicas)
            .build()
    }

    @Bean
    fun orderEventsDltTopic(properties: ProductMetricsTopicProperties): NewTopic {
        return TopicBuilder.name(properties.orderDltTopicName)
            .partitions(properties.partitions)
            .replicas(properties.replicas)
            .build()
    }
}

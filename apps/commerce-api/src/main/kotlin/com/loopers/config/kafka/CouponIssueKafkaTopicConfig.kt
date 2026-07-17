package com.loopers.config.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
@EnableConfigurationProperties(CouponIssueTopicProperties::class)
class CouponIssueKafkaTopicConfig {
    @Bean
    fun couponIssueRequestsTopic(properties: CouponIssueTopicProperties): NewTopic =
        TopicBuilder.name(properties.topicName)
            .partitions(properties.partitions)
            .replicas(properties.replicas)
            .build()

    @Bean
    fun couponIssueRequestsDltTopic(properties: CouponIssueTopicProperties): NewTopic =
        TopicBuilder.name(properties.dltTopicName)
            .partitions(properties.partitions)
            .replicas(properties.replicas)
            .build()
}

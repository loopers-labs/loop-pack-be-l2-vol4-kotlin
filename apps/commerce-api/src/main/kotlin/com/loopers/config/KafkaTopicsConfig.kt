package com.loopers.config

import com.loopers.coupon.infrastructure.messaging.CouponIssueRequestKafkaPublisher
import com.loopers.outbox.domain.EventTopics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.config.TopicBuilder

@Configuration
@Profile("local")
class KafkaTopicsConfig {
    @Bean
    fun productEventsTopic(): NewTopic = TopicBuilder.name(EventTopics.PRODUCT_EVENTS).partitions(3).replicas(1).build()

    @Bean
    fun couponIssueRequestsTopic(): NewTopic =
        TopicBuilder.name(CouponIssueRequestKafkaPublisher.COUPON_ISSUE_REQUESTS_TOPIC).partitions(1).replicas(1).build()

    @Bean
    fun orderEventsTopic(): NewTopic = TopicBuilder.name(EventTopics.ORDER_EVENTS).partitions(3).replicas(1).build()

    @Bean
    fun userActionEventsTopic(): NewTopic = TopicBuilder.name(EventTopics.USER_ACTION_EVENTS).partitions(3).replicas(1).build()
}

package com.loopers.config.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "commerce-events.product-metrics")
data class ProductMetricsTopicProperties(
    val catalogTopicName: String,
    val catalogDltTopicName: String,
    val orderTopicName: String,
    val orderDltTopicName: String,
    val partitions: Int,
    val replicas: Int,
)

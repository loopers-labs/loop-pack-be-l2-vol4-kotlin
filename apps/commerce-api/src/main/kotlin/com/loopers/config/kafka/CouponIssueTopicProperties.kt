package com.loopers.config.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "commerce-events.coupon-issue-request")
data class CouponIssueTopicProperties(
    val topicName: String,
    val dltTopicName: String,
    val partitions: Int,
    val replicas: Int,
)

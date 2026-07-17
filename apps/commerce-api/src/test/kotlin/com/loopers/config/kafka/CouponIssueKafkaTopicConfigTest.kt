package com.loopers.config.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class CouponIssueKafkaTopicConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withUserConfiguration(CouponIssueKafkaTopicConfig::class.java)
        .withPropertyValues(
            "commerce-events.coupon-issue-request.topic-name=configured-coupon-requests",
            "commerce-events.coupon-issue-request.dlt-topic-name=configured-coupon-failures",
            "commerce-events.coupon-issue-request.partitions=3",
            "commerce-events.coupon-issue-request.replicas=1",
        )

    @Test
    fun `쿠폰_발급_요청과_DLT_토픽을_명시적으로_생성한다`() {
        contextRunner.run { context ->
            val topics = context.getBeansOfType(NewTopic::class.java).values

            assertThat(topics.map(NewTopic::name))
                .containsExactlyInAnyOrder("configured-coupon-requests", "configured-coupon-failures")
            assertThat(topics.map(NewTopic::numPartitions)).containsOnly(3)
        }
    }
}

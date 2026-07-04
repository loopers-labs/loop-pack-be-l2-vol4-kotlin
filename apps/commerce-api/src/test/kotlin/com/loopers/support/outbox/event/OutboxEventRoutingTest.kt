package com.loopers.support.outbox.event

import com.loopers.support.outbox.OutboxEventModel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class OutboxEventRoutingTest {
    @ParameterizedTest
    @MethodSource("routes")
    fun `이벤트_type과_aggregateId로_topic과_key가_결정된다`(
        type: CommerceOutboxEventType,
        aggregateType: CommerceOutboxAggregateType,
        aggregateId: Long,
        expectedTopicName: String,
    ) {
        val event = OutboxEventModel(
            type = type.name,
            aggregateType = aggregateType.value,
            aggregateId = aggregateId,
            payload = "{}",
        )

        val route = OutboxEventRouting.route(event)

        assertThat(route.topicName).isEqualTo(expectedTopicName)
        assertThat(route.key).isEqualTo(aggregateId.toString())
    }

    @Test
    fun `알수없는_이벤트_type은_라우팅할_수_없다`() {
        val event = OutboxEventModel(
            type = "UNKNOWN_EVENT_V1",
            aggregateType = CommerceOutboxAggregateType.PRODUCT.value,
            aggregateId = 1L,
            payload = "{}",
        )

        assertThatThrownBy {
            OutboxEventRouting.route(event)
        }.hasMessageContaining("UNKNOWN_EVENT_V1")
    }

    companion object {
        @JvmStatic
        fun routes(): List<Arguments> = listOf(
            Arguments.of(
                CommerceOutboxEventType.LIKE_COUNT_CHANGED_V1,
                CommerceOutboxAggregateType.PRODUCT,
                101L,
                "catalog-events",
            ),
            Arguments.of(
                CommerceOutboxEventType.PRODUCT_VIEWED_V1,
                CommerceOutboxAggregateType.PRODUCT,
                301L,
                "catalog-events",
            ),
            Arguments.of(
                CommerceOutboxEventType.ORDER_CREATED_V1,
                CommerceOutboxAggregateType.ORDER,
                201L,
                "order-events",
            ),
            Arguments.of(
                CommerceOutboxEventType.ORDER_PAID_V1,
                CommerceOutboxAggregateType.ORDER,
                202L,
                "order-events",
            ),
            Arguments.of(
                CommerceOutboxEventType.ORDER_FAILED_V1,
                CommerceOutboxAggregateType.ORDER,
                203L,
                "order-events",
            ),
            Arguments.of(
                CommerceOutboxEventType.PAYMENT_APPROVED,
                CommerceOutboxAggregateType.PAYMENT,
                204L,
                "order-events",
            ),
            Arguments.of(
                CommerceOutboxEventType.PAYMENT_FAILED,
                CommerceOutboxAggregateType.PAYMENT,
                205L,
                "order-events",
            ),
            Arguments.of(
                CommerceOutboxEventType.COUPON_ISSUE_REQUESTED_V1,
                CommerceOutboxAggregateType.COUPON_ISSUE_REQUEST,
                401L,
                "coupon-issue-requests",
            ),
        )
    }
}

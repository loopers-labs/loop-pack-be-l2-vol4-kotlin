package com.loopers.support.event

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.config.kafka.CouponIssueTopicProperties
import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxRepository
import io.mockk.every
import io.mockk.slot
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

class CommerceApplicationEventOutboxListenerTest {
    private val outboxRepository = mockk<OutboxRepository>(relaxed = true)
    private val listener = CommerceApplicationEventOutboxListener(
        outboxRepository = outboxRepository,
        objectMapper = jacksonObjectMapper().findAndRegisterModules(),
        couponIssueTopicProperties = CouponIssueTopicProperties(
            topicName = "configured-coupon-requests",
            dltTopicName = "configured-coupon-failures",
            partitions = 3,
            replicas = 1,
        ),
    )

    @Test
    fun `결제_승인_이벤트는_자체_주문항목으로_발행가능_outbox를_만든다`() {
        val saved = slot<OutboxEventModel>()
        val items = listOf(CommerceEventOrderItem(productId = 20L, quantity = 3L))

        listener.onPaymentApproved(
            PaymentApprovedApplicationEvent(paymentId = 1L, orderId = 10L, items = items),
        )

        verify(exactly = 1) { outboxRepository.save(capture(saved)) }
        assertThat(saved.captured.topicName).isEqualTo("order-events")
        assertThat(saved.captured.partitionKey).isEqualTo("10")
        assertThat(saved.captured.payload).contains(
            """"orderId":10""",
            """"productId":20""",
            """"quantity":3""",
        )
    }

    @Test
    fun `결제_승인_outbox는_정수_범위를_넘는_주문_수량을_그대로_직렬화한다`() {
        val saved = slot<OutboxEventModel>()
        val largeQuantity = Int.MAX_VALUE.toLong() + 1

        listener.onPaymentApproved(
            PaymentApprovedApplicationEvent(
                paymentId = 1L,
                orderId = 10L,
                items = listOf(CommerceEventOrderItem(productId = 20L, quantity = largeQuantity)),
            ),
        )

        verify(exactly = 1) { outboxRepository.save(capture(saved)) }
        assertThat(saved.captured.payload).contains(""""quantity":$largeQuantity""")
    }

    @Test
    fun `좋아요_변경_이벤트는_자체_정보로_catalog_outbox를_만든다`() {
        val saved = slot<OutboxEventModel>()
        val occurredAt = ZonedDateTime.parse("2026-07-17T10:00:00+09:00[Asia/Seoul]")

        listener.onLikeChanged(
            LikeChangedApplicationEvent(userId = 3L, productId = 20L, delta = -1, occurredAt = occurredAt),
        )

        verify(exactly = 1) { outboxRepository.save(capture(saved)) }
        assertThat(saved.captured.type).isEqualTo("LIKE_COUNT_CHANGED_V1")
        assertThat(saved.captured.aggregateType).isEqualTo("PRODUCT")
        assertThat(saved.captured.aggregateId).isEqualTo(20L)
        assertThat(saved.captured.topicName).isEqualTo("catalog-events")
        assertThat(saved.captured.partitionKey).isEqualTo("20")
        assertThat(saved.captured.createdAt).isEqualTo(occurredAt)
        assertThat(saved.captured.payload).contains(
            """"productId":20""",
            """"userId":3""",
            """"delta":-1""",
        )
    }

    @Test
    fun `쿠폰_발급_요청_이벤트는_자체_정보로_template_key_outbox를_만든다`() {
        val saved = slot<OutboxEventModel>()
        val requestId = UUID.fromString("8a04bb25-cf9c-49e5-af4b-271531d99347")
        val occurredAt = ZonedDateTime.parse("2026-07-17T10:00:00+09:00[Asia/Seoul]")

        listener.onCouponIssueRequested(
            CouponIssueRequestedApplicationEvent(
                requestId = requestId,
                requestAggregateId = 91L,
                userId = 7L,
                couponTemplateId = 42L,
                occurredAt = occurredAt,
            ),
        )

        verify(exactly = 1) { outboxRepository.save(capture(saved)) }
        assertThat(saved.captured.type).isEqualTo("COUPON_ISSUE_REQUESTED_V1")
        assertThat(saved.captured.aggregateType).isEqualTo("COUPON_ISSUE_REQUEST")
        assertThat(saved.captured.aggregateId).isEqualTo(91L)
        assertThat(saved.captured.topicName).isEqualTo("configured-coupon-requests")
        assertThat(saved.captured.partitionKey).isEqualTo("42")
        assertThat(saved.captured.createdAt).isEqualTo(occurredAt)
        assertThat(saved.captured.payload).contains(
            requestId.toString(),
            """"userId":7""",
            """"couponTemplateId":42""",
        )
    }

    @Test
    fun `발행가능_outbox_저장_실패는_원천_트랜잭션에_전파된다`() {
        every { outboxRepository.save(any()) } throws IllegalStateException("outbox unavailable")

        assertThatThrownBy {
            listener.onOrderCreated(OrderCreatedApplicationEvent(orderId = 10L, items = emptyList()))
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("outbox unavailable")
    }
}

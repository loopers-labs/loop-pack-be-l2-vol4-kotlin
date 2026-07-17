package com.loopers.projection.like.application

import com.loopers.projection.like.port.ProcessedKafkaEventRepository
import com.loopers.projection.like.port.ProductLikeCountProjectionRepository
import com.loopers.projection.like.port.ProductMetricsUpdateStatus
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

class ProductMetricsProjectionServiceTest {
    @Test
    fun `여러_상품의_지표는_상품_ID_오름차순으로_갱신한다`() {
        val processedKafkaEventRepository = mockk<ProcessedKafkaEventRepository>()
        val projectionRepository = mockk<ProductLikeCountProjectionRepository>()
        val updatedProductIds = mutableListOf<Long>()
        every { processedKafkaEventRepository.recordIfAbsent(any(), any(), any()) } returns true
        every {
            projectionRepository.applyDelta(
                productId = any(),
                likeDelta = any(),
                salesDelta = any(),
                viewDelta = any(),
                occurredAt = any(),
            )
        } answers {
            updatedProductIds += firstArg<Long>()
            ProductMetricsUpdateStatus.APPLIED
        }
        val service = ProductMetricsProjectionService(processedKafkaEventRepository, projectionRepository)

        service.project(
            ProductMetricsProjectionCommand(
                eventId = UUID.randomUUID(),
                consumerGroup = "commerce-streamer-product-metrics",
                eventType = "ORDER_PAID_V1",
                occurredAt = ZonedDateTime.parse("2026-07-17T10:00:00+09:00"),
                deltas = listOf(
                    ProductMetricsDelta(productId = 30L, salesDelta = 1),
                    ProductMetricsDelta(productId = 10L, salesDelta = 1),
                    ProductMetricsDelta(productId = 20L, salesDelta = 1),
                ),
            ),
        )

        assertThat(updatedProductIds).containsExactly(10L, 20L, 30L)
    }
}

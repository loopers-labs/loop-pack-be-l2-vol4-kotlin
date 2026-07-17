package com.loopers.support.outbox

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OutboxEventModelTest {
    @Test
    fun `발행_경로는_topicName과_partitionKey가_함께_필수다`() {
        assertThatThrownBy {
            OutboxEventModel(
                type = "ORDER_PAID_V1",
                aggregateType = "ORDER",
                aggregateId = 1L,
                topicName = "order-events",
                partitionKey = null,
                payload = "{}",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}

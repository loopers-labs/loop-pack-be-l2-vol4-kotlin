package com.loopers.domain.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OutboxEventTest {
    @DisplayName("생성 시 eventId가 부여되고 상태는 PENDING이다.")
    @Test
    fun newOutboxEventIsPending() {
        val e = OutboxEvent(topic = "catalog-events", partitionKey = "10", payload = "{}")
        assertThat(e.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(e.eventId).isNotBlank()
        assertThat(e.sentAt).isNull()
    }

    @DisplayName("markSent 하면 SENT로 전이하고 sentAt이 채워진다.")
    @Test
    fun markSentTransitions() {
        val e = OutboxEvent(topic = "catalog-events", partitionKey = "10", payload = "{}")
        e.markSent()
        assertThat(e.status).isEqualTo(OutboxStatus.SENT)
        assertThat(e.sentAt).isNotNull()
    }
}

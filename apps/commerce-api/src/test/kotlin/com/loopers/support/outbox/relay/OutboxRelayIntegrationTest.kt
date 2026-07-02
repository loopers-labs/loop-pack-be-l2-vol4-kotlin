package com.loopers.support.outbox.relay

import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxEventStatus
import com.loopers.support.outbox.OutboxRepository
import com.loopers.utils.DatabaseCleanUp
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronizationManager

@SpringBootTest(
    properties = [
        "commerce-events.like-count.relay.enabled=false",
    ],
)
class OutboxRelayIntegrationTest
    @Autowired
    constructor(
        private val outboxRelay: OutboxRelay,
        private val outboxRepository: OutboxRepository,
        private val publisher: RecordingOutboxEventPublisher,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            publisher.reset()
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `claim된_이벤트는_트랜잭션_밖에서_발행되고_ack_후_PUBLISHED로_마킹된다`() {
            val saved = outboxRepository.save(likeCountEvent())

            val published = outboxRelay.publishOnce()

            assertThat(published).isEqualTo(1)
            assertThat(publisher.calls).hasSize(1)
            assertThat(publisher.calls.first().transactionActive).isFalse()
            assertThat(publisher.calls.first().event.status).isEqualTo(OutboxEventStatus.PUBLISHING)
            val updated = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(updated?.status).isEqualTo(OutboxEventStatus.PUBLISHED)
            assertThat(updated?.publishedAt).isNotNull()
        }

        @Test
        fun `브로커_ack가_실패하면_이벤트는_재시도_가능한_FAILED로_남는다`() {
            publisher.failWith = IllegalStateException("broker unavailable")
            val saved = outboxRepository.save(likeCountEvent())
            val beforePublish = ZonedDateTime.now()

            val published = outboxRelay.publishOnce()

            assertThat(published).isZero()
            assertThat(publisher.calls).hasSize(1)
            assertThat(publisher.calls.first().transactionActive).isFalse()
            val updated = outboxRepository.findByEventIdOrNull(saved.eventId)
            assertThat(updated?.status).isEqualTo(OutboxEventStatus.FAILED)
            assertThat(updated?.retryCount).isEqualTo(1)
            assertThat(updated?.lastError).contains("broker unavailable")
            assertThat(updated?.nextRetryAt).isAfter(beforePublish)
            assertThat(updated?.publishedAt).isNull()
        }

        private fun likeCountEvent(): OutboxEventModel =
            OutboxEventModel(
                type = "LIKE_COUNT_CHANGED_V1",
                aggregateType = "PRODUCT",
                aggregateId = 10L,
                payload = """{"productId":10,"userId":20,"delta":1}""",
            )

        @TestConfiguration
        class TestConfig {
            @Bean
            @Primary
            fun recordingOutboxEventPublisher(): RecordingOutboxEventPublisher = RecordingOutboxEventPublisher()
        }
    }

class RecordingOutboxEventPublisher : OutboxEventPublisher {
    val calls = mutableListOf<PublishCall>()
    var failWith: RuntimeException? = null

    override fun publish(event: OutboxEventModel) {
        calls.add(
            PublishCall(
                event = event,
                transactionActive = TransactionSynchronizationManager.isActualTransactionActive(),
            ),
        )
        failWith?.let { throw it }
    }

    fun reset() {
        calls.clear()
        failWith = null
    }
}

data class PublishCall(
    val event: OutboxEventModel,
    val transactionActive: Boolean,
)

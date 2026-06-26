package com.loopers.domain.payment.integration

import com.loopers.domain.payment.model.OutboxEventModel
import com.loopers.domain.payment.model.OutboxEventType
import com.loopers.domain.payment.port.OutboxRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OutboxRepositoryIntegrationTest
    @Autowired
    constructor(
        private val outboxRepository: OutboxRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `PENDING_상태의_특정_타입_이벤트만_조회한다`() {
            outboxRepository.save(이벤트(OutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED, aggregateId = 1L))
            outboxRepository.save(이벤트(OutboxEventType.PAYMENT_APPROVED, aggregateId = 2L))

            val pending = outboxRepository.findPendingByType(OutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED)

            assertThat(pending).hasSize(1)
            assertThat(pending.first().aggregateId).isEqualTo(1L)
        }

        @Test
        fun `처리완료로_마킹한_이벤트는_PENDING_조회에서_제외된다`() {
            val saved = outboxRepository.save(이벤트(OutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED, aggregateId = 1L))

            outboxRepository.markProcessed(saved.id)

            assertThat(outboxRepository.findPendingByType(OutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED)).isEmpty()
        }

        private fun 이벤트(type: OutboxEventType, aggregateId: Long): OutboxEventModel =
            OutboxEventModel(type = type, aggregateId = aggregateId, payload = """{"aggregateId":$aggregateId}""")
    }

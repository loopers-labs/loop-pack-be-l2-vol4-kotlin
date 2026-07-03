package com.loopers.outbox.infrastructure

import com.loopers.outbox.domain.OutboxEvent
import com.loopers.outbox.domain.OutboxEventRepository
import com.loopers.outbox.domain.OutboxStatus
import com.loopers.support.DatabaseCleanup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.ZonedDateTime

@SpringBootTest
@ActiveProfiles("test")
class OutboxEventRepositoryIntegrationTest @Autowired constructor(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    private fun saveEvent(aggregateId: Long): OutboxEvent =
        outboxEventRepository.save(OutboxEvent("ORDER", aggregateId, "OrderCreatedEvent", """{"eventId":"e-$aggregateId"}"""))

    @DisplayName("findPending 은 INIT 상태만 id 오름차순으로 limit 개까지 반환한다.")
    @Test
    fun findsOnlyInitInIdOrderUpToLimit() {
        val first = saveEvent(10L)
        val second = saveEvent(20L)
        val third = saveEvent(30L)
        outboxEventRepository.markSent(listOf(second.id))

        assertAll(
            { assertThat(outboxEventRepository.findPending(10).map { it.id }).containsExactly(first.id, third.id) },
            { assertThat(outboxEventRepository.findPending(1).map { it.id }).containsExactly(first.id) },
        )
    }

    @DisplayName("markSent 는 지정한 id 만 SENT 로 전이하고, 나머지는 INIT 을 유지한다.")
    @Test
    fun transitionsOnlyGivenIdsToSent() {
        val first = saveEvent(10L)
        val second = saveEvent(20L)

        val updated = outboxEventRepository.markSent(listOf(first.id))

        val statusById = outboxEventJpaRepository.findAll().associate { it.id to it.status }
        assertAll(
            { assertThat(updated).isEqualTo(1) },
            { assertThat(statusById[first.id]).isEqualTo(OutboxStatus.SENT) },
            { assertThat(statusById[second.id]).isEqualTo(OutboxStatus.INIT) },
        )
    }

    @DisplayName("deleteSentBefore 는 기준 시각 이전의 SENT 행만 삭제한다 — INIT 과 기준 이후 행은 남는다.")
    @Test
    fun deletesOnlySentBeforeThreshold() {
        val sent = saveEvent(10L)
        val pending = saveEvent(20L)
        outboxEventRepository.markSent(listOf(sent.id))

        val deletedBeforePast = outboxEventRepository.deleteSentBefore(ZonedDateTime.now().minusDays(3))
        val deletedBeforeFuture = outboxEventRepository.deleteSentBefore(ZonedDateTime.now().plusDays(1))

        assertAll(
            { assertThat(deletedBeforePast).isEqualTo(0) },
            { assertThat(deletedBeforeFuture).isEqualTo(1) },
            { assertThat(outboxEventJpaRepository.findAll().map { it.id }).containsExactly(pending.id) },
        )
    }
}

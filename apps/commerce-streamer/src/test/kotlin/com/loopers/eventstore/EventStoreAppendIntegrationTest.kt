package com.loopers.eventstore

import com.loopers.eventstore.application.EventStoreAppender
import com.loopers.eventstore.infrastructure.EventStoreJpaRepository
import com.loopers.support.DatabaseCleanup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class EventStoreAppendIntegrationTest @Autowired constructor(
    private val eventStoreAppender: EventStoreAppender,
    private val eventStoreJpaRepository: EventStoreJpaRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @DisplayName("이벤트를 append 하면 event_id 를 PK 로 원문 payload 가 저장된다.")
    @Test
    fun appendsRawPayloadWithEventIdAsKey() {
        eventStoreAppender.append("e-1", "product-events", """{"eventId":"e-1"}""".toByteArray())

        val stored = eventStoreJpaRepository.findById("e-1").orElseThrow()
        assertAll(
            { assertThat(stored.topic).isEqualTo("product-events") },
            { assertThat(stored.payload).isEqualTo("""{"eventId":"e-1"}""") },
        )
    }

    @DisplayName("같은 eventId 를 다시 append 하면 행이 늘지 않고 최초 payload 가 유지된다 — PK 자연 멱등.")
    @Test
    fun ignoresDuplicateAppend() {
        eventStoreAppender.append("e-1", "product-events", """{"eventId":"e-1","v":1}""".toByteArray())
        eventStoreAppender.append("e-1", "product-events", """{"eventId":"e-1","v":2}""".toByteArray())

        assertAll(
            { assertThat(eventStoreJpaRepository.count()).isEqualTo(1L) },
            { assertThat(eventStoreJpaRepository.findById("e-1").orElseThrow().payload).contains("\"v\":1") },
        )
    }
}

package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.infrastructure.event.repository.EventHandledJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EventHandledRepositoryImplIntegrationTest @Autowired constructor(
    private val eventHandledRepository: EventHandledRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("같은 eventId도 consumer group이 다르면 각각 처리 이력으로 저장한다")
    @Test
    fun savesSameEventIdForDifferentConsumerGroups() {
        eventHandledRepository.save(
            EventHandled(
                consumerGroup = "loopers-default-consumer",
                eventId = "event-1",
                eventType = "PRODUCT_VIEWED",
            ),
        )
        eventHandledRepository.save(
            EventHandled(
                consumerGroup = "commerce-coupon-issue",
                eventId = "event-1",
                eventType = "COUPON_ISSUE_REQUESTED",
            ),
        )

        assertAll(
            {
                assertThat(eventHandledRepository.exists("loopers-default-consumer", "event-1"))
                    .isTrue()
            },
            {
                assertThat(eventHandledRepository.exists("commerce-coupon-issue", "event-1"))
                    .isTrue()
            },
            { assertThat(eventHandledJpaRepository.findAll()).hasSize(2) },
        )
    }
}

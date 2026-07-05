package com.loopers.application.catalog

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.product.ProductStatProjection
import com.loopers.domain.product.ProductStatProjectionRepository
import com.loopers.domain.useraction.UserActionLog
import com.loopers.domain.useraction.UserActionLogRepository
import com.loopers.domain.useraction.UserActionType
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.ZonedDateTime

class CatalogEventProjectionServiceTest {
    @DisplayName("좋아요 이벤트를 상품 집계와 유저 행동 로그로 반영한다")
    @Test
    fun projectsLikedEvent() {
        val fixture = Fixture()
        val message = createMessage(eventId = "event-1", eventType = CatalogEventType.PRODUCT_LIKED)

        fixture.service.project(message)

        val productStat = fixture.productStatRepository.findByProductIdForUpdate(10L)
        val userActionLog = fixture.userActionLogRepository.logs.single()
        assertAll(
            { assertThat(productStat?.likeCount).isEqualTo(1L) },
            { assertThat(productStat?.latestEventVersion).isEqualTo(100L) },
            { assertThat(userActionLog.eventId).isEqualTo("event-1") },
            { assertThat(userActionLog.actionType).isEqualTo(UserActionType.PRODUCT_LIKED) },
            { assertThat(fixture.eventHandledRepository.exists("event-1")).isTrue() },
        )
    }

    @DisplayName("이미 처리한 이벤트는 다시 반영하지 않는다")
    @Test
    fun skipsAlreadyHandledEvent() {
        val fixture = Fixture()
        val message = createMessage(eventId = "event-1", eventType = CatalogEventType.PRODUCT_LIKED)

        fixture.service.project(message)
        fixture.service.project(message)

        val productStat = fixture.productStatRepository.findByProductIdForUpdate(10L)
        assertAll(
            { assertThat(productStat?.likeCount).isEqualTo(1L) },
            { assertThat(fixture.userActionLogRepository.logs).hasSize(1) },
        )
    }

    @DisplayName("조회 이벤트를 조회수와 유저 행동 로그로 반영한다")
    @Test
    fun projectsViewedEvent() {
        val fixture = Fixture()
        val message = createMessage(eventId = "event-1", eventType = CatalogEventType.PRODUCT_VIEWED)

        fixture.service.project(message)

        val productStat = fixture.productStatRepository.findByProductIdForUpdate(10L)
        val userActionLog = fixture.userActionLogRepository.logs.single()
        assertAll(
            { assertThat(productStat?.viewCount).isEqualTo(1L) },
            { assertThat(productStat?.latestEventVersion).isEqualTo(0L) },
            { assertThat(userActionLog.actionType).isEqualTo(UserActionType.PRODUCT_VIEWED) },
        )
    }

    private class Fixture {
        val eventHandledRepository = FakeEventHandledRepository()
        val productStatRepository = FakeProductStatProjectionRepository()
        val userActionLogRepository = FakeUserActionLogRepository()
        val service = CatalogEventProjectionService(
            eventHandledRepository = eventHandledRepository,
            productStatProjectionRepository = productStatRepository,
            userActionLogRepository = userActionLogRepository,
        )
    }

    private class FakeEventHandledRepository : EventHandledRepository {
        private val events = mutableSetOf<String>()

        override fun exists(eventId: String): Boolean {
            return eventId in events
        }

        override fun save(eventHandled: EventHandled): EventHandled {
            events.add(eventHandled.eventId)
            return eventHandled
        }
    }

    private class FakeProductStatProjectionRepository : ProductStatProjectionRepository {
        private val productStats = mutableMapOf<Long, ProductStatProjection>()

        override fun findByProductIdForUpdate(productId: Long): ProductStatProjection? {
            return productStats[productId]
        }

        override fun save(productStatProjection: ProductStatProjection): ProductStatProjection {
            productStats[productStatProjection.productId] = productStatProjection
            return productStatProjection
        }
    }

    private class FakeUserActionLogRepository : UserActionLogRepository {
        val logs = mutableListOf<UserActionLog>()

        override fun save(userActionLog: UserActionLog): UserActionLog {
            logs.add(userActionLog)
            return userActionLog
        }
    }

    private fun createMessage(
        eventId: String,
        eventType: CatalogEventType,
    ): CatalogEventMessage {
        return CatalogEventMessage(
            eventId = eventId,
            eventType = eventType,
            aggregateId = 10L,
            productId = 10L,
            brandId = 100L,
            memberId = 1L,
            version = 100L,
            occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }
}

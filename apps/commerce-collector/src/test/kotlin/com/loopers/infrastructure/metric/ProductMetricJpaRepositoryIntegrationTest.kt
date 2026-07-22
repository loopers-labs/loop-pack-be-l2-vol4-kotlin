package com.loopers.infrastructure.metric

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * upsert()는 커스텀 @Modifying 네이티브 쿼리라 호출 시점에 활성 트랜잭션이 필요하다(운영 코드에서는
 * ProductMetricProcessor의 @Transactional 경계 안에서 호출됨). 이 테스트는 리포지토리를 직접 호출하므로
 * 클래스 레벨 @Transactional로 그 경계를 대신한다.
 */
@Transactional
@SpringBootTest
class ProductMetricJpaRepositoryIntegrationTest @Autowired constructor(
    private val productMetricJpaRepository: ProductMetricJpaRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val baseOccurredAt = ZonedDateTime.parse("2026-06-07T10:00:00+09:00[Asia/Seoul]")
    private val baseDate = LocalDate.of(2026, 6, 7)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("upsert를 호출할 때, ")
    @Nested
    inner class Upsert {
        @DisplayName("신규 (productId, type, metricDate)이면, count가 delta로 신규 저장된다.")
        @Test
        fun insertsNewRow_whenKeyNotExists() {
            productMetricJpaRepository.upsert(productId = 1L, type = "LIKE", metricDate = baseDate, delta = 3L, occurredAt = baseOccurredAt)

            val found = productMetricJpaRepository.findById(ProductMetricId(productId = 1L, type = "LIKE", metricDate = baseDate))
            assertThat(found).isPresent
            assertThat(found.get().count).isEqualTo(3L)
        }

        @DisplayName("기존 키에 더 최신 occurredAt으로 upsert하면, count가 누적되고 updated_at이 갱신된다.")
        @Test
        fun accumulatesCount_whenOccurredAtIsNewer() {
            productMetricJpaRepository.upsert(productId = 1L, type = "LIKE", metricDate = baseDate, delta = 3L, occurredAt = baseOccurredAt)

            val newerOccurredAt = baseOccurredAt.plusMinutes(1)
            productMetricJpaRepository.upsert(productId = 1L, type = "LIKE", metricDate = baseDate, delta = 2L, occurredAt = newerOccurredAt)

            val found = productMetricJpaRepository.findById(ProductMetricId(1L, "LIKE", baseDate)).get()
            assertThat(found.count).isEqualTo(5L)
            assertThat(found.updatedAt.truncatedTo(ChronoUnit.SECONDS))
                .isEqualTo(newerOccurredAt.truncatedTo(ChronoUnit.SECONDS))
        }

        @DisplayName("기존 키보다 더 과거 occurredAt으로 upsert하면(늦게 도착한 오래된 이벤트), count와 updated_at이 변하지 않는다.")
        @Test
        fun ignoresUpdate_whenOccurredAtIsOlder() {
            productMetricJpaRepository.upsert(productId = 1L, type = "LIKE", metricDate = baseDate, delta = 3L, occurredAt = baseOccurredAt)

            val olderOccurredAt = baseOccurredAt.minusMinutes(1)
            productMetricJpaRepository.upsert(productId = 1L, type = "LIKE", metricDate = baseDate, delta = 10L, occurredAt = olderOccurredAt)

            val found = productMetricJpaRepository.findById(ProductMetricId(1L, "LIKE", baseDate)).get()
            assertThat(found.count).isEqualTo(3L)
            assertThat(found.updatedAt.truncatedTo(ChronoUnit.SECONDS))
                .isEqualTo(baseOccurredAt.truncatedTo(ChronoUnit.SECONDS))
        }

        @DisplayName("같은 productId라도 type이 다르면, 서로 다른 행으로 별도 집계된다.")
        @Test
        fun tracksSeparately_whenTypeDiffers() {
            productMetricJpaRepository.upsert(productId = 1L, type = "LIKE", metricDate = baseDate, delta = 3L, occurredAt = baseOccurredAt)
            productMetricJpaRepository.upsert(productId = 1L, type = "VIEW", metricDate = baseDate, delta = 7L, occurredAt = baseOccurredAt)

            assertThat(productMetricJpaRepository.findById(ProductMetricId(1L, "LIKE", baseDate)).get().count).isEqualTo(3L)
            assertThat(productMetricJpaRepository.findById(ProductMetricId(1L, "VIEW", baseDate)).get().count).isEqualTo(7L)
        }

        @DisplayName("같은 (productId, type)이라도 metricDate가 다르면, 날짜별로 별도 행에 누적된다 (일별화).")
        @Test
        fun tracksSeparately_whenMetricDateDiffers() {
            val nextDate = baseDate.plusDays(1)
            productMetricJpaRepository.upsert(productId = 1L, type = "LIKE", metricDate = baseDate, delta = 3L, occurredAt = baseOccurredAt)
            productMetricJpaRepository.upsert(productId = 1L, type = "LIKE", metricDate = nextDate, delta = 2L, occurredAt = baseOccurredAt.plusDays(1))

            assertThat(productMetricJpaRepository.findById(ProductMetricId(1L, "LIKE", baseDate)).get().count).isEqualTo(3L)
            assertThat(productMetricJpaRepository.findById(ProductMetricId(1L, "LIKE", nextDate)).get().count).isEqualTo(2L)
        }
    }

    @DisplayName("EventHandledJpaRepository는, ")
    @Nested
    inner class EventHandled {
        @DisplayName("처음 보는 eventId는 existsById가 false를 반환한다.")
        @Test
        fun returnsFalse_whenNeverSaved() {
            assertThat(eventHandledJpaRepository.existsById("event-1")).isFalse()
        }

        @DisplayName("저장한 eventId는 existsById가 true를 반환한다.")
        @Test
        fun returnsTrue_whenSaved() {
            eventHandledJpaRepository.save(EventHandledEntity(eventId = "event-1"))

            assertThat(eventHandledJpaRepository.existsById("event-1")).isTrue()
        }

        @DisplayName("동일 eventId를 다시 저장해도(수동 할당 ID라 save가 merge로 동작), 예외 없이 행이 하나로 유지된다.")
        @Test
        fun staysSingleRow_whenEventIdSavedTwice() {
            eventHandledJpaRepository.saveAndFlush(EventHandledEntity(eventId = "event-1"))
            eventHandledJpaRepository.saveAndFlush(EventHandledEntity(eventId = "event-1"))

            assertThat(eventHandledJpaRepository.existsById("event-1")).isTrue()
            assertThat(eventHandledJpaRepository.count()).isEqualTo(1L)
        }
    }
}

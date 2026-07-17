package com.loopers.application.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.ranking.RankingScorePolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.log10

class RankingEventMapperTest {
    private val mapper = RankingEventMapper(
        objectMapper = ObjectMapper(),
        scorePolicy = RankingScorePolicy(viewWeight = 0.1, likeWeight = 0.2, orderWeight = 0.6),
    )

    @DisplayName("PRODUCT_VIEWED는 +0.1 델타 엔트리로 매핑된다.")
    @Test
    fun mapsProductViewed() {
        val entry = mapper.toEntry("""{"eventId":"e1","type":"PRODUCT_VIEWED","productId":10}""")!!
        assertThat(entry.eventId).isEqualTo("e1")
        assertThat(entry.deltas).hasSize(1)
        assertThat(entry.deltas[0].productId).isEqualTo(10L)
        assertThat(entry.deltas[0].score).isEqualTo(0.1)
    }

    @DisplayName("LIKE_ADDED는 +0.2, LIKE_REMOVED는 -0.2로 매핑된다.")
    @Test
    fun mapsLikeEvents() {
        val added = mapper.toEntry("""{"eventId":"e2","type":"LIKE_ADDED","productId":10}""")!!
        val removed = mapper.toEntry("""{"eventId":"e3","type":"LIKE_REMOVED","productId":10}""")!!
        assertThat(added.deltas[0].score).isEqualTo(0.2)
        assertThat(removed.deltas[0].score).isEqualTo(-0.2)
    }

    @DisplayName("PAYMENT_SUCCEEDED는 아이템별 0.6×log10(1+단가×수량) 델타로 매핑된다.")
    @Test
    fun mapsPaymentSucceeded() {
        val entry = mapper.toEntry(
            """{"eventId":"e4","type":"PAYMENT_SUCCEEDED","orderId":1,"userId":2,
               "items":[{"productId":10,"quantity":2,"unitPrice":15000.00}]}""",
        )!!
        assertThat(entry.deltas[0].productId).isEqualTo(10L)
        assertThat(entry.deltas[0].score)
            .isCloseTo(0.6 * log10(1.0 + 30000.0), org.assertj.core.data.Offset.offset(1e-9))
    }

    @DisplayName("unitPrice가 없는 아이템은 건너뛴다 (구버전 페이로드 호환).")
    @Test
    fun skipsItemWithoutUnitPrice() {
        val entry = mapper.toEntry(
            """{"eventId":"e5","type":"PAYMENT_SUCCEEDED","items":[{"productId":10,"quantity":2}]}""",
        )!!
        assertThat(entry.deltas).isEmpty()
    }

    @DisplayName("알 수 없는 타입/eventId 결손은 null을 반환한다.")
    @Test
    fun returnsNullForUnknown() {
        assertThat(mapper.toEntry("""{"eventId":"e6","type":"COUPON_ISSUE_REQUESTED"}""")).isNull()
        assertThat(mapper.toEntry("""{"type":"PRODUCT_VIEWED","productId":10}""")).isNull()
    }
}

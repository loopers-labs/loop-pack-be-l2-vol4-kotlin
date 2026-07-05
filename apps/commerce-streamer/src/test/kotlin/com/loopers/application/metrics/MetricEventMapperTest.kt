package com.loopers.application.metrics

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MetricEventMapperTest {
    private val mapper = MetricEventMapper(ObjectMapper())

    @DisplayName("LIKE_ADDED 는 like=+1 delta로 매핑된다.")
    @Test
    fun mapsLikeAdded() {
        val cmd = mapper.toCommand("""{"eventId":"e1","type":"LIKE_ADDED","productId":10}""")!!
        assertThat(cmd.eventId).isEqualTo("e1")
        assertThat(cmd.deltas).containsExactly(MetricDelta(productId = 10L, like = 1))
    }

    @DisplayName("LIKE_REMOVED 는 like=-1 delta로 매핑된다.")
    @Test
    fun mapsLikeRemoved() {
        val cmd = mapper.toCommand("""{"eventId":"e2","type":"LIKE_REMOVED","productId":10}""")!!
        assertThat(cmd.deltas).containsExactly(MetricDelta(productId = 10L, like = -1))
    }

    @DisplayName("PRODUCT_VIEWED 는 view=+1 delta로 매핑된다.")
    @Test
    fun mapsProductViewed() {
        val cmd = mapper.toCommand("""{"eventId":"e3","type":"PRODUCT_VIEWED","productId":10}""")!!
        assertThat(cmd.deltas).containsExactly(MetricDelta(productId = 10L, view = 1))
    }

    @DisplayName("PAYMENT_SUCCEEDED 는 item별 sales delta로 매핑된다.")
    @Test
    fun mapsPaymentSucceeded() {
        val json = """{"eventId":"e4","type":"PAYMENT_SUCCEEDED","orderId":1,"userId":2,"items":[{"productId":10,"quantity":3},{"productId":20,"quantity":1}]}"""
        val cmd = mapper.toCommand(json)!!
        assertThat(cmd.deltas).containsExactly(
            MetricDelta(productId = 10L, sales = 3),
            MetricDelta(productId = 20L, sales = 1),
        )
    }

    @DisplayName("알 수 없는 type은 null(무시).")
    @Test
    fun ignoresUnknownType() {
        assertThat(mapper.toCommand("""{"eventId":"e5","type":"WHATEVER"}""")).isNull()
    }

    @DisplayName("known type이라도 productId가 없으면 null(무시), NPE를 던지지 않는다.")
    @Test
    fun ignoresMissingProductIdInsteadOfThrowing() {
        assertThat(mapper.toCommand("""{"eventId":"e6","type":"LIKE_ADDED"}""")).isNull()
    }
}

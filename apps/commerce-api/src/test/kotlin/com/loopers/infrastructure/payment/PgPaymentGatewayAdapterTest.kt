package com.loopers.infrastructure.payment

import com.loopers.infrastructure.payment.PgPaymentGatewayAdapter.Companion.toPgOrderId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PgPaymentGatewayAdapterTest {

    @Nested
    @DisplayName("PG 로 전달할 주문 ID 변환은")
    inner class ToPgOrderId {
        @Test
        @DisplayName("6자리 미만이면 앞을 0으로 채워 6자리로 만든다.")
        fun padsShortOrderId() {
            assertThat(toPgOrderId(1L)).isEqualTo("000001")
            assertThat(toPgOrderId(12345L)).isEqualTo("012345")
        }

        @Test
        @DisplayName("이미 6자리 이상이면 그대로 둔다.")
        fun keepsLongOrderId() {
            assertThat(toPgOrderId(100000L)).isEqualTo("100000")
            assertThat(toPgOrderId(1234567L)).isEqualTo("1234567")
        }
    }
}

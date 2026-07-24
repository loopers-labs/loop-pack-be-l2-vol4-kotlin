package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RankedProductIdTest {
    @Test
    fun `상품 ID가 0 이하이면 생성할 수 없다`() {
        assertThatThrownBy {
            RankedProductId(productId = 0L, rank = 1L)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `순위가 0 이하이면 생성할 수 없다`() {
        assertThatThrownBy {
            RankedProductId(productId = 1L, rank = 0L)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}

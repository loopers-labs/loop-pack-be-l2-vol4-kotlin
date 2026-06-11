package com.loopers.infrastructure.like

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LikeCountEntityTest {
    @DisplayName("increment()는 count를 1 증가시킨다.")
    @Test
    fun incrementsCount() {
        val entity = LikeCountEntity(productId = 10L, count = 0)

        entity.increment()

        assertThat(entity.count).isEqualTo(1)
    }

    @DisplayName("increment()를 여러 번 호출하면 누적해 증가한다.")
    @Test
    fun incrementsAccumulates() {
        val entity = LikeCountEntity(productId = 10L, count = 0)

        repeat(3) { entity.increment() }

        assertThat(entity.count).isEqualTo(3)
    }

    @DisplayName("decrement()는 count가 0보다 클 때 1 감소시킨다.")
    @Test
    fun decrementsCount_whenPositive() {
        val entity = LikeCountEntity(productId = 10L, count = 3)

        entity.decrement()

        assertThat(entity.count).isEqualTo(2)
    }

    @DisplayName("decrement()는 count가 0이면 변경하지 않는다 (음수 방지).")
    @Test
    fun preventsNegative_whenZero() {
        val entity = LikeCountEntity(productId = 10L, count = 0)

        entity.decrement()

        assertThat(entity.count).isEqualTo(0)
    }
}

package com.loopers.domain.like

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LikeTest {
    @DisplayName("Like 생성 시, userId가 0 이하이면 IllegalArgumentException이 발생한다.")
    @Test
    fun throws_whenUserIdIsNotPositive() {
        assertThrows<IllegalArgumentException> {
            Like(userId = 0L, productId = 1L)
        }
    }

    @DisplayName("Like 생성 시, productId가 0 이하이면 IllegalArgumentException이 발생한다.")
    @Test
    fun throws_whenProductIdIsNotPositive() {
        assertThrows<IllegalArgumentException> {
            Like(userId = 1L, productId = 0L)
        }
    }

    @DisplayName("정상 값이 주어지면, Like를 생성한다.")
    @Test
    fun createsLike() {
        val like = Like(userId = 1L, productId = 10L)

        assertThat(like.userId).isEqualTo(1L)
        assertThat(like.productId).isEqualTo(10L)
        assertThat(like.id).isEqualTo(0L)
    }
}

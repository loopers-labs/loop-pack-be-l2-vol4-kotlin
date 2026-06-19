package com.loopers.domain.catalog

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductStatsTest {
    @DisplayName("좋아요 수는 증가하고 감소할 수 있다.")
    @Test
    fun changesLikeCount() {
        val stats = ProductStats(productId = 1L, likeCount = 0)

        stats.increaseLikeCount()
        stats.decreaseLikeCount()

        assertThat(stats.likeCount).isZero()
    }

    @DisplayName("좋아요 수는 0 미만이 될 수 없다.")
    @Test
    fun rejectsNegativeLikeCount() {
        val stats = ProductStats(productId = 1L, likeCount = 0)

        val ex = assertThrows<CoreException> {
            stats.decreaseLikeCount()
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}

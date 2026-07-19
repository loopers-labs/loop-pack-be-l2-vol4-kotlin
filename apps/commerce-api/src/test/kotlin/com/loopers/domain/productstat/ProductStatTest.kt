package com.loopers.domain.productstat

import com.loopers.domain.product.model.ProductStat
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductStatTest {
    @DisplayName("상품 통계 생성")
    @Nested
    inner class Create {
        @DisplayName("브랜드 ID는 양수여야 한다")
        @Test
        fun throwsBadRequest_whenBrandIdIsNotPositive() {
            val result = assertThrows<CoreException> {
                ProductStat(productId = 1L, brandId = 0L, likeCount = 0L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("좋아요 수는 음수로 시작할 수 없다")
        @Test
        fun throwsBadRequest_whenLikeCountIsNegative() {
            val result = assertThrows<CoreException> {
                ProductStat(productId = 1L, brandId = 1L, likeCount = -1L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}

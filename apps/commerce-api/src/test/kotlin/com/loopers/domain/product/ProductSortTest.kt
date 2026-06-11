package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductSortTest {

    @DisplayName("ProductSort.from 호출 시, ")
    @Nested
    inner class From {
        @DisplayName("null이면 LATEST를 반환한다.")
        @Test
        fun returnsLatest_whenNull() {
            assertThat(ProductSort.from(null)).isEqualTo(ProductSort.LATEST)
        }

        @DisplayName("빈 문자열이면 LATEST를 반환한다.")
        @Test
        fun returnsLatest_whenBlank() {
            assertThat(ProductSort.from("")).isEqualTo(ProductSort.LATEST)
            assertThat(ProductSort.from("   ")).isEqualTo(ProductSort.LATEST)
        }

        @DisplayName("등록된 raw 값을 정확히 매핑한다.")
        @Test
        fun mapsRawValues() {
            assertThat(ProductSort.from("latest")).isEqualTo(ProductSort.LATEST)
            assertThat(ProductSort.from("price_asc")).isEqualTo(ProductSort.PRICE_ASC)
            assertThat(ProductSort.from("likes_desc")).isEqualTo(ProductSort.LIKES_DESC)
        }

        @DisplayName("등록되지 않은 값이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenUnknown() {
            val result = assertThrows<CoreException> { ProductSort.from("unknown") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("대소문자가 다르면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenCaseDiffers() {
            val result = assertThrows<CoreException> { ProductSort.from("LATEST") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}

package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductSortTest {
    @DisplayName("정렬 값이 비어 있으면 latest 를 기본값으로 사용한다")
    @Test
    fun returnsLatest_whenSortValueIsBlank() {
        assertThat(ProductSort.from(null)).isEqualTo(ProductSort.LATEST)
        assertThat(ProductSort.from("")).isEqualTo(ProductSort.LATEST)
    }

    @DisplayName("지원하는 상품 정렬 값을 파싱한다")
    @Test
    fun parsesProductSort_whenValueIsSupported() {
        assertThat(ProductSort.from("latest")).isEqualTo(ProductSort.LATEST)
        assertThat(ProductSort.from("price_asc")).isEqualTo(ProductSort.PRICE_ASC)
        assertThat(ProductSort.from("likes_desc")).isEqualTo(ProductSort.LIKES_DESC)
    }

    @DisplayName("지원하지 않는 정렬 값이면 실패한다")
    @Test
    fun throwsBadRequest_whenValueIsUnsupported() {
        val result = assertThrows<CoreException> {
            ProductSort.from("unknown")
        }

        assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}

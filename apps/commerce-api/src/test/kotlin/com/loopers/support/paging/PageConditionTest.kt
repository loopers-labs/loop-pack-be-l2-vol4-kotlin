package com.loopers.support.paging

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class PageConditionTest {
    @DisplayName("페이징 조건 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("페이지 번호가 0 이상이고 페이지 크기가 1 이상 100 이하이면 생성된다.")
        @Test
        fun create_whenPageAndSizeAreValid() {
            // act
            val pageCondition = PageCondition(page = 0, size = 20)

            // assert
            assertAll(
                { assertThat(pageCondition.page).isZero() },
                { assertThat(pageCondition.size).isEqualTo(20) },
            )
        }

        @DisplayName("페이지 번호가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPageIsNegative() {
            // act & assert
            val result = assertThrows<CoreException> { PageCondition(page = -1, size = 20) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("페이지 크기가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenSizeIsLessThanOne() {
            // act & assert
            val result = assertThrows<CoreException> { PageCondition(page = 0, size = 0) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("페이지 크기가 100보다 크면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenSizeIsGreaterThanOneHundred() {
            // act & assert
            val result = assertThrows<CoreException> { PageCondition(page = 0, size = 101) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}

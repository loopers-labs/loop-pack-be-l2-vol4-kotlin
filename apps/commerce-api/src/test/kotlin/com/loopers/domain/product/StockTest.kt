package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class StockTest {
    @DisplayName("상품 재고 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("0 이상의 재고이면 정상적으로 생성된다.")
        @Test
        fun create_whenValueIsZeroOrPositive() {
            // act
            val stock = Stock(10)

            // assert
            assertThat(stock.value).isEqualTo(10)
        }

        @DisplayName("재고가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenValueIsNegative() {
            // act & assert
            val result = assertThrows<CoreException> { Stock(-1) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상품 재고 차감 가능 여부 검증 시, ")
    @Nested
    inner class ValidateDeductible {
        @DisplayName("재고가 충분하면 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenStockIsEnough() {
            // arrange
            val stock = Stock(10)

            // act & assert
            assertDoesNotThrow { stock.validateDeductible(StockQuantity(3)) }
        }

        @DisplayName("재고와 수량이 같으면 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenStockEqualsQuantity() {
            // arrange
            val stock = Stock(3)

            // act & assert
            assertDoesNotThrow { stock.validateDeductible(StockQuantity(3)) }
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            val stock = Stock(2)

            // act & assert
            val result = assertThrows<CoreException> { stock.validateDeductible(StockQuantity(3)) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상품 재고 품절 여부 확인 시, ")
    @Nested
    inner class IsEmpty {
        @DisplayName("재고가 0이면 품절이다.")
        @Test
        fun isEmpty_whenValueIsZero() {
            // act & assert
            assertThat(Stock(0).isEmpty()).isTrue()
        }

        @DisplayName("재고가 1 이상이면 품절이 아니다.")
        @Test
        fun isEmpty_whenValueIsPositive() {
            // act & assert
            assertThat(Stock(1).isEmpty()).isFalse()
        }
    }
}

package com.loopers.domain.catalog

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class ProductStockTest {
    @DisplayName("ProductStock 은 실제 재고 수량만 증가시킨다.")
    @Test
    fun addStockIncreasesStockQuantity() {
        val stock = ProductStock(productId = 1L, stockQuantity = 3)

        stock.add(2)

        assertThat(stock.stockQuantity).isEqualTo(5)
    }

    @DisplayName("차감 수량이 실제 재고보다 크면 BAD_REQUEST 예외를 던진다.")
    @Test
    fun deductThrowsBadRequestWhenQuantityExceedsStock() {
        val stock = ProductStock(productId = 1L, stockQuantity = 1)

        val ex = assertThrows<CoreException> {
            stock.deduct(2)
        }

        assertAll(
            { assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
            { assertThat(stock.stockQuantity).isEqualTo(1) },
        )
    }

    @DisplayName("0 이하 수량으로 재고를 변경하면 BAD_REQUEST 예외를 던진다.")
    @Test
    fun rejectsNonPositiveQuantity() {
        val stock = ProductStock(productId = 1L, stockQuantity = 1)

        val ex = assertThrows<CoreException> {
            stock.add(0)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}

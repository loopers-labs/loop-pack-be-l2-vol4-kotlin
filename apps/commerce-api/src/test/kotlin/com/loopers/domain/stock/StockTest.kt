package com.loopers.domain.stock

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StockTest {

    @DisplayName("Stock.create 호출 시, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 값이면 Stock을 생성한다.")
        @Test
        fun createsStock_whenValid() {
            val stock = Stock.create(productId = 1L, quantity = 10)
            assertThat(stock.productId).isEqualTo(1L)
            assertThat(stock.quantity).isEqualTo(10)
        }

        @DisplayName("quantity가 음수이면 예외가 발생한다.")
        @Test
        fun throwsException_whenQuantityNegative() {
            assertThrows<IllegalArgumentException> { Stock.create(productId = 1L, quantity = -1) }
        }

        @DisplayName("productId가 0 이하이면 예외가 발생한다.")
        @Test
        fun throwsException_whenProductIdNotPositive() {
            assertThrows<IllegalArgumentException> { Stock.create(productId = 0L, quantity = 10) }
        }
    }

    @DisplayName("decrease 호출 시, ")
    @Nested
    inner class Decrease {
        @DisplayName("amount만큼 quantity가 감소한 새 Stock을 반환한다.")
        @Test
        fun decreasesQuantity_whenValid() {
            val stock = Stock(id = 1L, productId = 1L, quantity = 10)
            val decreased = stock.decrease(3)
            assertThat(decreased.quantity).isEqualTo(7)
            assertThat(stock.quantity).isEqualTo(10) // 원본 불변
        }

        @DisplayName("amount가 0 이하이면 예외가 발생한다.")
        @Test
        fun throwsException_whenAmountNotPositive() {
            val stock = Stock(id = 1L, productId = 1L, quantity = 10)
            assertThrows<IllegalArgumentException> { stock.decrease(0) }
            assertThrows<IllegalArgumentException> { stock.decrease(-1) }
        }

        @DisplayName("재고가 부족하면 예외가 발생한다.")
        @Test
        fun throwsException_whenInsufficient() {
            val stock = Stock(id = 1L, productId = 1L, quantity = 5)
            val result = assertThrows<IllegalArgumentException> { stock.decrease(10) }
            assertThat(result.message).contains("재고가 부족")
        }
    }

    @DisplayName("restore 호출 시, ")
    @Nested
    inner class Restore {
        @DisplayName("amount만큼 quantity가 증가한 새 Stock을 반환한다.")
        @Test
        fun increasesQuantity_whenValid() {
            val stock = Stock(id = 1L, productId = 1L, quantity = 5)
            val restored = stock.restore(3)
            assertThat(restored.quantity).isEqualTo(8)
        }

        @DisplayName("amount가 0 이하이면 예외가 발생한다.")
        @Test
        fun throwsException_whenAmountNotPositive() {
            val stock = Stock(id = 1L, productId = 1L, quantity = 5)
            assertThrows<IllegalArgumentException> { stock.restore(0) }
        }
    }

    @DisplayName("updateQuantity 호출 시, ")
    @Nested
    inner class UpdateQuantity {
        @DisplayName("주어진 값으로 quantity가 덮어쓰인 새 Stock을 반환한다.")
        @Test
        fun overwritesQuantity_whenValid() {
            val stock = Stock(id = 1L, productId = 1L, quantity = 5)
            val updated = stock.updateQuantity(100)
            assertThat(updated.quantity).isEqualTo(100)
        }

        @DisplayName("0으로 덮어쓰는 것은 허용된다.")
        @Test
        fun allowsZero() {
            val stock = Stock(id = 1L, productId = 1L, quantity = 5)
            val updated = stock.updateQuantity(0)
            assertThat(updated.quantity).isEqualTo(0)
        }

        @DisplayName("음수로 덮어쓰면 예외가 발생한다.")
        @Test
        fun throwsException_whenNegative() {
            val stock = Stock(id = 1L, productId = 1L, quantity = 5)
            assertThrows<IllegalArgumentException> { stock.updateQuantity(-1) }
        }
    }
}

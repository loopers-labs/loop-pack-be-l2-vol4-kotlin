package com.loopers.domain.stock

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StockServiceTest {

    private lateinit var stockRepositoryPort: StockRepositoryPort
    private lateinit var stockService: StockService

    @BeforeEach
    fun setUp() {
        stockRepositoryPort = mockk()
        stockService = StockService(stockRepositoryPort)
    }

    @DisplayName("getByProductId 호출 시")
    @Nested
    inner class GetByProductId {
        @DisplayName("재고가 존재하면 도메인 객체를 반환한다.")
        @Test
        fun returnsStock_whenExists() {
            val stock = Stock(id = 1L, productId = 10L, quantity = 5)
            every { stockRepositoryPort.findByProductId(10L) } returns stock

            val result = stockService.getByProductId(10L)

            assertThat(result).isEqualTo(stock)
        }

        @DisplayName("재고가 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            every { stockRepositoryPort.findByProductId(any()) } returns null

            val result = assertThrows<CoreException> { stockService.getByProductId(9999L) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("decrease 호출 시")
    @Nested
    inner class Decrease {
        @DisplayName("재고가 충분하면 차감된 Stock을 저장하고 반환한다.")
        @Test
        fun decreasesStock_whenSufficient() {
            val existing = Stock(id = 1L, productId = 10L, quantity = 5)
            every { stockRepositoryPort.findByProductId(10L) } returns existing
            val captured = slot<Stock>()
            every { stockRepositoryPort.save(capture(captured)) } answers { captured.captured }

            val result = stockService.decrease(productId = 10L, quantity = 3)

            assertThat(result.quantity).isEqualTo(2)
            assertThat(captured.captured.productId).isEqualTo(10L)
            verify(exactly = 1) { stockRepositoryPort.save(any()) }
        }

        @DisplayName("재고가 부족하면 IllegalArgumentException 이 발생하고 save는 호출되지 않는다.")
        @Test
        fun throwsWhenInsufficient() {
            val existing = Stock(id = 1L, productId = 10L, quantity = 2)
            every { stockRepositoryPort.findByProductId(10L) } returns existing

            assertThrows<IllegalArgumentException> { stockService.decrease(productId = 10L, quantity = 5) }
            verify(exactly = 0) { stockRepositoryPort.save(any()) }
        }

        @DisplayName("재고가 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            every { stockRepositoryPort.findByProductId(any()) } returns null

            val result = assertThrows<CoreException> { stockService.decrease(productId = 10L, quantity = 1) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
            verify(exactly = 0) { stockRepositoryPort.save(any()) }
        }
    }

    @DisplayName("restore 호출 시")
    @Nested
    inner class Restore {
        @DisplayName("복원 수량만큼 Stock 이 증가된 결과를 저장하고 반환한다.")
        @Test
        fun restoresStock() {
            val existing = Stock(id = 1L, productId = 10L, quantity = 2)
            every { stockRepositoryPort.findByProductId(10L) } returns existing
            val captured = slot<Stock>()
            every { stockRepositoryPort.save(capture(captured)) } answers { captured.captured }

            val result = stockService.restore(productId = 10L, quantity = 3)

            assertThat(result.quantity).isEqualTo(5)
            assertThat(captured.captured.productId).isEqualTo(10L)
            verify(exactly = 1) { stockRepositoryPort.save(any()) }
        }

        @DisplayName("재고가 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            every { stockRepositoryPort.findByProductId(any()) } returns null

            val result = assertThrows<CoreException> { stockService.restore(productId = 10L, quantity = 1) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
            verify(exactly = 0) { stockRepositoryPort.save(any()) }
        }
    }
}

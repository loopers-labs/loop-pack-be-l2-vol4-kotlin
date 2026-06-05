package com.loopers.domain.product.unit

import com.loopers.domain.product.application.service.StockService
import com.loopers.domain.product.model.StockModel
import com.loopers.domain.product.port.StockRepository
import com.loopers.domain.product.support.ProductSteps.Companion.재고_도메인_생성
import com.loopers.domain.product.support.ProductSteps.Companion.재고_차감_커맨드
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StockServiceTest {
    @Test
    fun `초기_재고를_생성한다`() {
        val stockRepository = mockk<StockRepository>()
        val stockService = StockService(stockRepository)
        val stockSlot = slot<StockModel>()
        every { stockRepository.save(capture(stockSlot)) } answers { stockSlot.captured }

        val stock = stockService.initialize(productId = 1L, leftStock = 10)

        assertThat(stock.productId).isEqualTo(1L)
        assertThat(stock.leftStock.value).isEqualTo(10)
    }

    @Test
    fun `여러_재고를_차감한다`() {
        val stockRepository = mockk<StockRepository>()
        val stockService = StockService(stockRepository)
        val stockSlot = slot<List<StockModel>>()
        every { stockRepository.findByProductIdsForUpdate(listOf(1L, 2L)) } returns listOf(
            재고_도메인_생성(productId = 1L, leftStock = 10),
            재고_도메인_생성(productId = 2L, leftStock = 5),
        )
        every { stockRepository.saveAll(capture(stockSlot)) } answers { stockSlot.captured }

        val stocks = stockService.decreaseAll(
            listOf(
                재고_차감_커맨드(productId = 1L, quantity = 3),
                재고_차감_커맨드(productId = 2L, quantity = 2),
            ),
        )

        assertThat(stocks.map { it.productId to it.leftStock.value })
            .containsExactly(1L to 7L, 2L to 3L)
    }

    @Test
    fun `동일_상품_차감_요청은_수량을_합산한다`() {
        val stockRepository = mockk<StockRepository>()
        val stockService = StockService(stockRepository)
        val stockSlot = slot<List<StockModel>>()
        every { stockRepository.findByProductIdsForUpdate(listOf(1L)) } returns listOf(
            재고_도메인_생성(productId = 1L, leftStock = 10),
        )
        every { stockRepository.saveAll(capture(stockSlot)) } answers { stockSlot.captured }

        val stocks = stockService.decreaseAll(
            listOf(
                재고_차감_커맨드(productId = 1L, quantity = 3),
                재고_차감_커맨드(productId = 1L, quantity = 4),
            ),
        )

        assertThat(stocks.single().leftStock.value).isEqualTo(3)
    }

    @Test
    fun `재고_락은_상품_ID_오름차순으로_요청한다`() {
        val stockRepository = mockk<StockRepository>()
        val stockService = StockService(stockRepository)
        every { stockRepository.findByProductIdsForUpdate(listOf(1L, 2L)) } returns listOf(
            재고_도메인_생성(productId = 1L, leftStock = 10),
            재고_도메인_생성(productId = 2L, leftStock = 5),
        )
        every { stockRepository.saveAll(any()) } answers { firstArg() }

        val stocks = stockService.decreaseAll(
            listOf(
                재고_차감_커맨드(productId = 2L, quantity = 2),
                재고_차감_커맨드(productId = 1L, quantity = 3),
            ),
        )

        assertThat(stocks.map { it.productId }).containsExactly(1L, 2L)
    }

    @Test
    fun `하나라도_재고가_부족하면_어떤_재고도_저장하지_않는다`() {
        val stockRepository = mockk<StockRepository>()
        val stockService = StockService(stockRepository)
        every { stockRepository.findByProductIdsForUpdate(listOf(1L, 2L)) } returns listOf(
            재고_도메인_생성(productId = 1L, leftStock = 10),
            재고_도메인_생성(productId = 2L, leftStock = 2),
        )

        val ex = assertThrows<CoreException> {
            stockService.decreaseAll(
                listOf(
                    재고_차감_커맨드(productId = 1L, quantity = 3),
                    재고_차감_커맨드(productId = 2L, quantity = 3),
                ),
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
        verify(exactly = 0) { stockRepository.saveAll(any()) }
    }

    @Test
    fun `각_차감_요청_수량은_양수여야_한다`() {
        val stockRepository = mockk<StockRepository>()
        val stockService = StockService(stockRepository)

        val ex = assertThrows<CoreException> {
            stockService.decreaseAll(
                listOf(
                    재고_차감_커맨드(productId = 1L, quantity = 3),
                    재고_차감_커맨드(productId = 1L, quantity = -2),
                ),
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        verify(exactly = 0) { stockRepository.findByProductIdsForUpdate(any()) }
        verify(exactly = 0) { stockRepository.saveAll(any()) }
    }
}

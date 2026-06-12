package com.loopers.domain.product.integration

import com.loopers.domain.product.application.service.StockService
import com.loopers.domain.product.infrastructure.persistence.stock.ProductStockJpaRepository
import com.loopers.domain.product.support.ProductSteps.Companion.재고_차감_커맨드
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class StockServiceIntegrationTest
    @Autowired
    constructor(
        private val stockService: StockService,
        private val productStockJpaRepository: ProductStockJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `초기_재고를_저장한다`() {
            stockService.initialize(productId = 1L, leftStock = 10)

            val saved = productStockJpaRepository.findById(1L).orElseThrow()
            assertThat(saved.leftStock).isEqualTo(10)
        }

        @Test
        fun `재고를_차감하면_변경된_재고가_저장된다`() {
            stockService.initialize(productId = 1L, leftStock = 10)

            stockService.decreaseAll(listOf(재고_차감_커맨드(productId = 1L, quantity = 3)))

            val saved = productStockJpaRepository.findById(1L).orElseThrow()
            assertThat(saved.leftStock).isEqualTo(7)
        }

        @Test
        fun `재고가_부족하면_CONFLICT가_발생하고_재고를_저장하지_않는다`() {
            stockService.initialize(productId = 1L, leftStock = 2)

            val ex = assertThrows<CoreException> {
                stockService.decreaseAll(listOf(재고_차감_커맨드(productId = 1L, quantity = 3)))
            }

            val saved = productStockJpaRepository.findById(1L).orElseThrow()
            assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(saved.leftStock).isEqualTo(2)
        }

        @Test
        fun `서로_다른_순서의_동시_재고차감도_완료된다`() {
            stockService.initialize(productId = 1L, leftStock = 10)
            stockService.initialize(productId = 2L, leftStock = 10)
            val executor = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)

            try {
                val first = executor.submit {
                    ready.countDown()
                    start.await()
                    stockService.decreaseAll(
                        listOf(
                            재고_차감_커맨드(productId = 1L, quantity = 1),
                            재고_차감_커맨드(productId = 2L, quantity = 1),
                        ),
                    )
                }
                val second = executor.submit {
                    ready.countDown()
                    start.await()
                    stockService.decreaseAll(
                        listOf(
                            재고_차감_커맨드(productId = 2L, quantity = 1),
                            재고_차감_커맨드(productId = 1L, quantity = 1),
                        ),
                    )
                }

                assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue()
                start.countDown()
                first.get(5, TimeUnit.SECONDS)
                second.get(5, TimeUnit.SECONDS)

                assertThat(productStockJpaRepository.findById(1L).orElseThrow().leftStock).isEqualTo(8)
                assertThat(productStockJpaRepository.findById(2L).orElseThrow().leftStock).isEqualTo(8)
            } finally {
                executor.shutdownNow()
            }
        }
    }

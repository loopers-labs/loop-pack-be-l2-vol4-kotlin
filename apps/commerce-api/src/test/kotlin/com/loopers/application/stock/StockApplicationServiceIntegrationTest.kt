package com.loopers.application.stock

import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class StockApplicationServiceIntegrationTest @Autowired constructor(
    private val stockApplicationService: StockApplicationService,
    private val stockJpaRepository: StockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("재고 차감 및 복구 시, ")
    @Nested
    inner class DeductAndRestore {
        @DisplayName("재고를 차감하면 수량이 줄어든다.")
        @Test
        fun deduct_decreasesQuantity() {
            // arrange
            val stock = stockJpaRepository.save(StockJpaEntity(productId = 1L, quantity = 10))

            // act
            stockApplicationService.deduct(productId = stock.productId, amount = 3)

            // assert
            val remaining = stockApplicationService.getStock(stock.productId)
            assertThat(remaining.quantity).isEqualTo(7)
        }

        @DisplayName("재고를 복구하면 수량이 늘어난다.")
        @Test
        fun restore_increasesQuantity() {
            // arrange
            val stock = stockJpaRepository.save(StockJpaEntity(productId = 1L, quantity = 5))
            stockApplicationService.deduct(productId = stock.productId, amount = 5)

            // act
            stockApplicationService.restore(productId = stock.productId, amount = 5)

            // assert
            val restored = stockApplicationService.getStock(stock.productId)
            assertThat(restored.quantity).isEqualTo(5)
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생하고 수량은 변하지 않는다.")
        @Test
        fun throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            val stock = stockJpaRepository.save(StockJpaEntity(productId = 1L, quantity = 2))

            // act & assert
            val result = assertThrows<CoreException> {
                stockApplicationService.deduct(productId = stock.productId, amount = 3)
            }

            val unchanged = stockApplicationService.getStock(stock.productId)
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(unchanged.quantity).isEqualTo(2)
        }
    }

    @DisplayName("재고 동시 차감 시, ")
    @Nested
    inner class ConcurrentDeduction {
        @DisplayName("재고가 1개일 때 10개의 동시 요청이 들어오면 정확히 1개만 성공한다.")
        @Test
        fun deductStock_succeedsOnlyOnce_whenConcurrentRequestsExceedStock() {
            // arrange
            val stock = stockJpaRepository.save(StockJpaEntity(productId = 1L, quantity = 1))
            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)

            // act
            repeat(threadCount) {
                executor.submit {
                    try {
                        stockApplicationService.deduct(
                            productId = stock.productId,
                            amount = 1,
                        )
                        successCount.incrementAndGet()
                    } catch (e: CoreException) {
                        failCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
            executor.shutdown()

            // assert
            val remaining = stockApplicationService.getStock(stock.productId)
            assertThat(successCount.get()).isEqualTo(1)
            assertThat(failCount.get()).isEqualTo(9)
            assertThat(remaining.quantity).isEqualTo(0)
        }

        @DisplayName("재고가 5개일 때 10개의 동시 요청(각 1개씩)이 들어오면 정확히 5개만 성공한다.")
        @Test
        fun deductStock_succeedsExactlyFiveTimes_whenConcurrentRequestsExceedStock() {
            // arrange
            val stock = stockJpaRepository.save(StockJpaEntity(productId = 1L, quantity = 5))
            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)

            // act
            repeat(threadCount) {
                executor.submit {
                    try {
                        stockApplicationService.deduct(
                            productId = stock.productId,
                            amount = 1,
                        )
                        successCount.incrementAndGet()
                    } catch (e: CoreException) {
                        failCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
            executor.shutdown()

            // assert
            val remaining = stockApplicationService.getStock(stock.productId)
            assertThat(successCount.get()).isEqualTo(5)
            assertThat(failCount.get()).isEqualTo(5)
            assertThat(remaining.quantity).isEqualTo(0)
        }
    }
}

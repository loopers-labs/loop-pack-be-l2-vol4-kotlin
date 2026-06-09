package com.loopers.domain.stock

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class StockServiceConcurrencyIntegrationTest @Autowired constructor(
    private val stockService: StockService,
    private val stockRepositoryPort: StockRepositoryPort,
    private val transactionManager: PlatformTransactionManager,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일 상품에 10개 스레드가 동시에 1개씩 차감해도, 재고가 정확히 0까지만 차감되고 음수가 되지 않는다.")
    @Test
    fun decreasesExactlyToZero_underConcurrency() {
        // setup: 재고 10개 (테스트 메서드에 @Transactional 없음 → 즉시 커밋되어 워커 스레드가 본다)
        val productId = 1L
        val initialStock = 10
        val threadCount = 10
        stockRepositoryPort.save(Stock.create(productId = productId, quantity = initialStock))

        // 워커별 트랜잭션 경계. 프로덕션의 OrderPlacement.place(@Transactional) 경계를 시뮬레이션해
        // read(FOR UPDATE)~write 가 한 트랜잭션에 묶이도록 한다. (도메인 서비스/테스트 메서드에는 @Transactional 금지 규칙)
        val txTemplate = TransactionTemplate(transactionManager)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(32)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)

        repeat(threadCount) {
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    txTemplate.execute { stockService.decrease(productId = productId, quantity = 1) }
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await()
        start.countDown() // 동시 출발
        done.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        val finalStock = stockRepositoryPort.findByProductId(productId)
        assertThat(finalStock).isNotNull
        assertThat(finalStock?.quantity).isEqualTo(0) // 음수 아님 + 정확히 0
        assertThat(successCount.get()).isEqualTo(initialStock) // 10건 모두 성공
        assertThat(failCount.get()).isEqualTo(threadCount - initialStock) // 초과 차감 없음 (0건 실패)
    }
}

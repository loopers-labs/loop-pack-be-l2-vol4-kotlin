package com.loopers.domain.like

import com.loopers.infrastructure.like.LikeCountEntity
import com.loopers.infrastructure.like.LikeCountJpaRepository
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
class LikeServiceConcurrencyIntegrationTest @Autowired constructor(
    private val likeService: LikeService,
    private val likeCountJpaRepository: LikeCountJpaRepository,
    private val transactionManager: PlatformTransactionManager,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("서로 다른 N명이 동일 상품에 동시에 좋아요를 눌러도, 좋아요 수가 정확히 N으로 반영된다.")
    @Test
    fun likeCountEqualsN_underConcurrency() {
        // setup: 카운트 행 0으로 커밋 (테스트 메서드에 @Transactional 없음 → 워커 스레드가 즉시 본다)
        val productId = 1L
        val threadCount = 10
        likeCountJpaRepository.saveAndFlush(LikeCountEntity(productId = productId, count = 0))

        // 워커별 트랜잭션 경계. 프로덕션의 LikeApplicationServiceAdapter.like(@Transactional) 경계를 시뮬레이션해
        // 비관적 락(read~write)이 한 트랜잭션에 묶이도록 한다. (도메인 서비스/테스트 메서드엔 @Transactional 금지 규칙)
        val txTemplate = TransactionTemplate(transactionManager)

        val executor = Executors.newFixedThreadPool(32)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)

        repeat(threadCount) { idx ->
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    txTemplate.execute { likeService.register(userId = (idx + 1).toLong(), productId = productId) }
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await()
        start.countDown() // 동시 출발
        done.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        val finalCount = likeCountJpaRepository.findByProductId(productId)
        assertThat(finalCount).isNotNull
        assertThat(finalCount?.count).isEqualTo(threadCount) // Lost Update 없이 정확히 N
    }

    @DisplayName("좋아요 N명 후 서로 다른 M명이 동시에 취소해도, 좋아요 수가 정확히 N-M으로 반영되고 음수가 되지 않는다.")
    @Test
    fun likeCountEqualsNMinusM_underConcurrentCancel() {
        // setup: N명이 좋아요한 상태를 커밋
        val productId = 1L
        val likeCount = 10
        val cancelCount = 6
        val txTemplate = TransactionTemplate(transactionManager)
        likeCountJpaRepository.saveAndFlush(LikeCountEntity(productId = productId, count = 0))
        repeat(likeCount) { idx ->
            txTemplate.execute { likeService.register(userId = (idx + 1).toLong(), productId = productId) }
        }

        val executor = Executors.newFixedThreadPool(32)
        val ready = CountDownLatch(cancelCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(cancelCount)
        val canceled = AtomicInteger(0)

        repeat(cancelCount) { idx ->
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    txTemplate.execute { likeService.cancel(userId = (idx + 1).toLong(), productId = productId) }
                    canceled.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await()
        start.countDown() // 동시 출발
        done.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        val finalCount = likeCountJpaRepository.findByProductId(productId)
        assertThat(finalCount).isNotNull
        assertThat(canceled.get()).isEqualTo(cancelCount)
        assertThat(finalCount?.count).isEqualTo(likeCount - cancelCount) // 음수 없이 정확히 N-M
    }
}

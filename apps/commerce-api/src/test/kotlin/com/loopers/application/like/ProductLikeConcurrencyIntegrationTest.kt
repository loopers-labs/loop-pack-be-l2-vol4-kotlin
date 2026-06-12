package com.loopers.application.like

import com.loopers.domain.catalog.ProductStats
import com.loopers.infrastructure.catalog.ProductStatsJpaRepository
import com.loopers.infrastructure.like.ProductLikeHistoryJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class ProductLikeConcurrencyIntegrationTest @Autowired constructor(
    private val productLikeFacade: ProductLikeFacade,
    private val productLikeHistoryJpaRepository: ProductLikeHistoryJpaRepository,
    private val productStatsJpaRepository: ProductStatsJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동시에 같은 상품에 좋아요 등록을 여러 번 요청해도 REGISTER 이력과 Catalog 증가가 한 번만 발생한다.")
    @Test
    fun serializesConcurrentRegisterRequests() {
        productStatsJpaRepository.save(ProductStats(productId = 100L))

        runConcurrently(times = 8) {
            productLikeFacade.register(userId = 1L, productId = 100L)
        }

        val stats = productStatsJpaRepository.findByProductIdAndDeletedAtIsNull(100L)

        assertAll(
            { assertThat(productLikeHistoryJpaRepository.countByUserIdAndProductId(1L, 100L)).isEqualTo(1) },
            { assertThat(stats?.likeCount).isEqualTo(1) },
            { assertThat(productLikeFacade.isLiked(userId = 1L, productId = 100L).liked).isTrue() },
        )
    }

    @DisplayName("좋아요 상태에서 동시에 취소를 여러 번 요청해도 CANCEL 이력과 Catalog 감소가 한 번만 발생한다.")
    @Test
    fun serializesConcurrentCancelRequests() {
        productStatsJpaRepository.save(ProductStats(productId = 100L))
        productLikeFacade.register(userId = 1L, productId = 100L)

        runConcurrently(times = 8) {
            productLikeFacade.cancel(userId = 1L, productId = 100L)
        }

        val stats = productStatsJpaRepository.findByProductIdAndDeletedAtIsNull(100L)

        assertAll(
            { assertThat(productLikeHistoryJpaRepository.countByUserIdAndProductId(1L, 100L)).isEqualTo(2) },
            { assertThat(stats?.likeCount).isEqualTo(0) },
            { assertThat(productLikeFacade.isLiked(userId = 1L, productId = 100L).liked).isFalse() },
        )
    }

    @DisplayName("여러 사용자가 동시에 같은 상품에 좋아요를 등록해도 Catalog 좋아요 수가 모든 등록 수만큼 증가한다.")
    @Test
    fun countsConcurrentRegistersFromDifferentUsers() {
        productStatsJpaRepository.save(ProductStats(productId = 100L))

        runConcurrently(times = 12) { index ->
            productLikeFacade.register(userId = index + 1L, productId = 100L)
        }

        val stats = productStatsJpaRepository.findByProductIdAndDeletedAtIsNull(100L)

        assertAll(
            { assertThat(productLikeHistoryJpaRepository.count()).isEqualTo(12) },
            { assertThat(stats?.likeCount).isEqualTo(12) },
        )
    }

    @DisplayName("여러 사용자가 동시에 같은 상품의 좋아요를 취소해도 Catalog 좋아요 수가 0으로 수렴한다.")
    @Test
    fun countsConcurrentCancelsFromDifferentUsers() {
        productStatsJpaRepository.save(ProductStats(productId = 100L))
        repeat(12) { index ->
            productLikeFacade.register(userId = index + 1L, productId = 100L)
        }

        runConcurrently(times = 12) { index ->
            productLikeFacade.cancel(userId = index + 1L, productId = 100L)
        }

        val stats = productStatsJpaRepository.findByProductIdAndDeletedAtIsNull(100L)

        assertAll(
            { assertThat(productLikeHistoryJpaRepository.count()).isEqualTo(24) },
            { assertThat(stats?.likeCount).isEqualTo(0) },
        )
    }

    private fun runConcurrently(times: Int, block: (Int) -> Unit) {
        val executor = Executors.newFixedThreadPool(times)
        val ready = CountDownLatch(times)
        val start = CountDownLatch(1)
        val done = CountDownLatch(times)
        val failures = mutableListOf<Throwable>()

        repeat(times) {
            executor.submit {
                try {
                    ready.countDown()
                    start.await(3, TimeUnit.SECONDS)
                    block(it)
                } catch (t: Throwable) {
                    synchronized(failures) { failures += t }
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await(3, TimeUnit.SECONDS)
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(failures).isEmpty()
    }
}

package com.loopers.domain.like.integration

import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.like.infrastructure.persistence.LikeJpaRepository
import com.loopers.domain.like.infrastructure.persistence.ProductLikeCountJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class ProductLikeCountConcurrencyIntegrationTest
    @Autowired
    constructor(
        private val likeService: LikeService,
        private val likeJpaRepository: LikeJpaRepository,
        private val productLikeCountJpaRepository: ProductLikeCountJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `상품_생성_시_좋아요_수는_0으로_시작한다`() {
            likeService.initializeCount(상품_ID)

            assertThat(likeService.countByProductId(상품_ID)).isZero()
        }

        @Test
        fun `동일_상품에_N명이_동시에_좋아요하면_카운트와_레코드가_N으로_일치한다`() {
            likeService.initializeCount(상품_ID)
            val 사용자_수 = 50

            동시_실행(사용자_수) { userId ->
                likeService.like(userId = (userId + 1).toLong(), productId = 상품_ID)
            }

            assertThat(likeService.countByProductId(상품_ID)).isEqualTo(사용자_수.toLong())
            assertThat(likeJpaRepository.count()).isEqualTo(사용자_수.toLong())
        }

        @Test
        fun `같은_사용자가_같은_상품을_동시에_연타해도_카운트는_1이다`() {
            likeService.initializeCount(상품_ID)
            val 요청_수 = 50

            동시_실행(요청_수) {
                likeService.like(userId = 1L, productId = 상품_ID)
            }

            assertThat(likeService.countByProductId(상품_ID)).isEqualTo(1L)
            assertThat(likeJpaRepository.count()).isEqualTo(1L)
        }

        @Test
        fun `좋아요와_취소가_혼재해도_카운트는_레코드_수와_일치한다`() {
            likeService.initializeCount(상품_ID)
            val 사용자_수 = 40
            (1..사용자_수).forEach { userId ->
                likeService.like(userId = userId.toLong(), productId = 상품_ID)
            }

            // 짝수 사용자만 취소. 홀수 사용자는 좋아요를 다시 시도(멱등).
            동시_실행(사용자_수) { index ->
                val userId = (index + 1).toLong()
                if (userId % 2 == 0L) {
                    likeService.unlike(userId = userId, productId = 상품_ID)
                } else {
                    likeService.like(userId = userId, productId = 상품_ID)
                }
            }

            val expected = (사용자_수 / 2).toLong()
            assertThat(likeJpaRepository.count()).isEqualTo(expected)
            assertThat(likeService.countByProductId(상품_ID)).isEqualTo(expected)
        }

        @Test
        fun `좋아요_취소를_연타해도_카운트는_음수가_되지_않는다`() {
            likeService.initializeCount(상품_ID)
            likeService.like(userId = 1L, productId = 상품_ID)
            val 요청_수 = 50

            동시_실행(요청_수) {
                likeService.unlike(userId = 1L, productId = 상품_ID)
            }

            assertThat(likeService.countByProductId(상품_ID)).isZero()
            assertThat(likeJpaRepository.count()).isZero()
        }

        private fun 동시_실행(
            count: Int,
            task: (index: Int) -> Unit,
        ) {
            val executor = Executors.newFixedThreadPool(count)
            val ready = CountDownLatch(count)
            val start = CountDownLatch(1)
            try {
                val futures = (0 until count).map { index ->
                    executor.submit {
                        ready.countDown()
                        start.await()
                        task(index)
                    }
                }
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
                start.countDown()
                futures.forEach { it.get(10, TimeUnit.SECONDS) }
            } finally {
                executor.shutdownNow()
            }
        }

        companion object {
            private const val 상품_ID = 10L
        }
    }

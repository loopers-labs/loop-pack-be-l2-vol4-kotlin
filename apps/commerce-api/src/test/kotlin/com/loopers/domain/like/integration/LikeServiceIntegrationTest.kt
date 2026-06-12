package com.loopers.domain.like.integration

import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.like.infrastructure.persistence.LikeJpaRepository
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
class LikeServiceIntegrationTest
    @Autowired
    constructor(
        private val likeService: LikeService,
        private val likeJpaRepository: LikeJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `좋아요_등록은_사용자와_상품_쌍으로_멱등하다`() {
            likeService.initializeCount(productId = 10L)

            likeService.like(userId = 1L, productId = 10L)
            likeService.like(userId = 1L, productId = 10L)

            assertThat(likeJpaRepository.count()).isEqualTo(1)
            assertThat(likeService.countByProductId(10L)).isEqualTo(1)
        }

        @Test
        fun `동시에_같은_상품을_좋아요해도_하나만_저장된다`() {
            likeService.initializeCount(productId = 10L)

            executeConcurrently(List(2) { 1L }) { userId ->
                likeService.like(userId = userId, productId = 10L)
            }

            assertThat(likeJpaRepository.count()).isEqualTo(1)
            assertThat(likeService.countByProductId(10L)).isEqualTo(1)
        }

        @Test
        fun `여러_사용자가_동시에_같은_상품을_좋아요하면_카운트가_요청수와_일치한다`() {
            likeService.initializeCount(productId = 10L)
            val userIds = List(10) { index -> index + 1L }

            executeConcurrently(userIds) { userId ->
                likeService.like(userId = userId, productId = 10L)
            }

            assertThat(likeJpaRepository.count()).isEqualTo(10)
            assertThat(likeService.countByProductId(10L)).isEqualTo(10)
        }

        @Test
        fun `좋아요_취소는_좋아요를_삭제한다`() {
            likeService.initializeCount(productId = 10L)
            likeService.like(userId = 1L, productId = 10L)

            likeService.unlike(userId = 1L, productId = 10L)

            assertThat(likeJpaRepository.count()).isZero()
            assertThat(likeService.countByProductId(10L)).isZero()
        }

        @Test
        fun `상품별_좋아요_수를_집계한다`() {
            likeService.initializeCount(productId = 10L)
            likeService.initializeCount(productId = 20L)
            likeService.like(userId = 1L, productId = 10L)
            likeService.like(userId = 2L, productId = 10L)
            likeService.like(userId = 1L, productId = 20L)

            val counts = likeService.countByProductIds(setOf(10L, 20L))

            assertThat(counts).containsEntry(10L, 2L)
            assertThat(counts).containsEntry(20L, 1L)
        }

        private fun <T> executeConcurrently(targets: List<T>, action: (T) -> Unit) {
            val executor = Executors.newFixedThreadPool(targets.size)
            val ready = CountDownLatch(targets.size)
            val start = CountDownLatch(1)

            try {
                val futures = targets.map { target ->
                    executor.submit {
                        ready.countDown()
                        start.await()
                        action(target)
                    }
                }

                assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue()
                start.countDown()
                futures.forEach { future -> future.get(5, TimeUnit.SECONDS) }
            } finally {
                executor.shutdownNow()
            }
        }
    }

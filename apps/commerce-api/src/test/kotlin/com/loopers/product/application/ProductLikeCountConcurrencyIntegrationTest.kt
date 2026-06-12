package com.loopers.product.application

import com.loopers.like.application.LikeService
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.shared.domain.Money
import com.loopers.support.DatabaseCleanup
import com.loopers.support.awaitUntil
import com.loopers.support.runConcurrently
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ProductLikeCountConcurrencyIntegrationTest @Autowired constructor(
    private val likeService: LikeService,
    private val productRepository: ProductRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @DisplayName("동일한 상품에 여러 명이 동시에 좋아요를 요청해도, 좋아요 수가 정확히 반영된다.")
    @Test
    fun increasesLikeCountExactly_whenLikedConcurrently() {
        val product = productRepository.save(Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(10_000)))

        val failures = runConcurrently(threadCount = 10) { index ->
            likeService.like(userId = (index + 1).toLong(), productId = product.id)
        }

        assertThat(failures).isEmpty()
        awaitUntil { productRepository.findActiveById(product.id)!!.likeCount == 10L }
    }

    @DisplayName("동일한 상품에 여러 명이 동시에 좋아요 취소를 요청해도, 좋아요 수가 정확히 반영된다.")
    @Test
    fun decreasesLikeCountExactly_whenUnlikedConcurrently() {
        val product = productRepository.save(Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(10_000)))
        (1L..10L).forEach { likeService.like(userId = it, productId = product.id) }
        awaitUntil { productRepository.findActiveById(product.id)!!.likeCount == 10L }

        val failures = runConcurrently(threadCount = 10) { index ->
            likeService.unlike(userId = (index + 1).toLong(), productId = product.id)
        }

        assertThat(failures).isEmpty()
        awaitUntil { productRepository.findActiveById(product.id)!!.likeCount == 0L }
    }
}
